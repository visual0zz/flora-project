package com.flora.sanctum.vault.formats.opvault;

import com.flora.root.codec.json.JsonParser;
import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonValue;
import com.flora.sanctum.kdbx.KdbxDocument;
import com.flora.sanctum.vault.formats.VaultFormat;
import com.flora.sanctum.vault.formats.VaultReadException;
import com.flora.sanctum.vault.formats.VaultReadException.Stage;
import com.flora.sanctum.vault.formats.VaultReader;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 1Password OPVault（1Password Vault）只读读取器。
 * <p>OPVault 是一个目录（本读取器以 ZIP 字节传入）：其中 {@code profile.js} 持有主密钥与派生参数，
 * {@code folders.js} 描述分组，{@code band_*.js} 逐条存放条目（每文件一段 UUID→条目 的映射，
 * 敏感字段以 opdata01 形式内嵌）。解密层次（已用独立 Python 探针验证）如下：
 * <ol>
 *   <li>PBKDF2-SHA512(主密码, profile.salt, profile.iterations, 64B) → encKey(32) + hmacKey(32)；</li>
 *   <li>profile.masterKey / overviewKey 为 opdata01（用 encKey/hmacKey 解密）→ 其明文再 SHA-512
 *       拆成 masterEnc/masterHmac 与 overviewEnc/overviewHmac；</li>
 *   <li>条目的 {@code k} 不是 opdata01：其结构为 IV(16)+AES-CBC(masterEnc)对 64B 明文的密文(80)+
 *       HMAC-SHA256(32)；解密出的 64B 明文直接拆成 itemEnc(32)+itemHmac(32)（此处不经 SHA-512）；</li>
 *   <li>条目的 {@code o} / {@code d} 是 opdata01（分别用 overview / item 的密钥解密），明文为 JSON：
 *       {@code o} 为概览（title/url/URLs），{@code d} 为明细（password/notesPlain/fields/sections）。</li>
 * </ol>
 * <p>opdata01 布局：magic "opdata01"(8) + LE u64 明文长度(8) + IV(16) + AES-CBC 密文 + HMAC-SHA256(32)；
 * 明文前附加 1–16 字节随机前缀（使总长度对齐 16），解密后跳过该前缀取真实明文。</p>
 * <p>限制：仅支持口令保护的 OPVault（不使用密钥文件，{@code keyFile} 被忽略）；文件夹层级（folders.js）
 * 在本只读导入中折叠为按类别（category）分组；未实现的附件等不导入。</p>
 */
public final class OpVaultReader implements VaultReader {

    private static final String PROFILE_PREFIX = "var profile=";
    private static final String FOLDERS_PREFIX = "loadFolders(";
    private static final String BAND_PREFIX = "ld(";

    /** 1Password 已知类别 → 分组名（未知类别回退为 "Category_<代码>"）。 */
    private static final Map<String, String> CATEGORY_NAMES = new LinkedHashMap<>();
    static {
        CATEGORY_NAMES.put("001", "Login");
        CATEGORY_NAMES.put("002", "Credit Card");
        CATEGORY_NAMES.put("003", "Secure Note");
        CATEGORY_NAMES.put("004", "Identity");
        CATEGORY_NAMES.put("005", "Password");
        CATEGORY_NAMES.put("006", "Tombstone");
        CATEGORY_NAMES.put("100", "Software License");
        CATEGORY_NAMES.put("101", "Bank Account");
        CATEGORY_NAMES.put("102", "Database");
        CATEGORY_NAMES.put("103", "Driver License");
        CATEGORY_NAMES.put("104", "Outdoor License");
        CATEGORY_NAMES.put("105", "Membership");
        CATEGORY_NAMES.put("106", "Passport");
        CATEGORY_NAMES.put("107", "Rewards");
        CATEGORY_NAMES.put("108", "Social Security Number");
        CATEGORY_NAMES.put("109", "Router");
        CATEGORY_NAMES.put("110", "Server");
        CATEGORY_NAMES.put("111", "Email");
        CATEGORY_NAMES.put("112", "API Credential");
    }

    @Override
    public VaultFormat format() {
        return VaultFormat.OPVAULT;
    }

    @Override
    public KdbxDocument read(byte[] data, char[] password, byte[] keyFile) throws VaultReadException {
        // 1) 解析 ZIP 内的 js 文件
        String profileJs = null;
        String foldersJs = null;
        List<String> bands = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                byte[] content = readAll(zis);
                String text = new String(content, StandardCharsets.UTF_8);
                if (name.endsWith("/profile.js") || name.equals("profile.js")) {
                    if (profileJs == null) {
                        profileJs = text;
                    }
                } else if (name.endsWith("/folders.js") || name.equals("folders.js")) {
                    foldersJs = text;
                } else if (name.matches(".*/band_[0-9A-Fa-f]+\\.js") || name.matches("band_[0-9A-Fa-f]+\\.js")) {
                    bands.add(text);
                }
            }
        } catch (Exception ex) {
            throw fail(Stage.STRUCTURE, "OPVault 不是合法 ZIP 或读取失败", ex);
        }
        if (profileJs == null) {
            throw fail(Stage.STRUCTURE, "OPVault 缺少 profile.js（不是有效的 1Password Vault）");
        }

        // 2) 解析 profile，派生口令密钥
        JsonObject profile = parseJs(profileJs, PROFILE_PREFIX, ";");
        String saltB64 = profile.getString("salt");
        Integer iterations = profile.getInt("iterations");
        String masterKeyB64 = profile.getString("masterKey");
        String overviewKeyB64 = profile.getString("overviewKey");
        if (saltB64 == null || iterations == null || masterKeyB64 == null || overviewKeyB64 == null) {
            throw fail(Stage.HEADER, "profile.js 缺少派生参数或主密钥字段");
        }
        byte[] salt = Base64.getDecoder().decode(saltB64);
        char[] pw = (password == null) ? new char[0] : password;
        byte[] derived = pbkdf2HmacSha512(pw, salt, iterations, 64);
        byte[] encKey = slice(derived, 0, 32);
        byte[] hmacKey = slice(derived, 32, 32);

        // 3) 解密主密钥 / 概览密钥 → 各自的 (enc, hmac)
        byte[] masterPlain = opdata01Decrypt(masterKeyB64, encKey, hmacKey);
        byte[] overviewPlain = opdata01Decrypt(overviewKeyB64, encKey, hmacKey);
        byte[] masterEnc = slice(sha512(masterPlain), 0, 32);
        byte[] masterHmac = slice(sha512(masterPlain), 32, 32);
        byte[] overviewEnc = slice(sha512(overviewPlain), 0, 32);
        byte[] overviewHmac = slice(sha512(overviewPlain), 32, 32);

        // 4) 组装文档：按类别建分组
        KdbxDocument.KdbxGroup root = new KdbxDocument.KdbxGroup();
        root.name = "Imported";
        Map<String, KdbxDocument.KdbxGroup> categoryGroups = new LinkedHashMap<>();

        for (String bandText : bands) {
            JsonObject band = parseJs(bandText, BAND_PREFIX, ");");
            if (band == null) {
                continue;
            }
            for (String key : band.keySet()) {
                JsonValue iv = band.get(key);
                if (iv == null || !iv.isObject()) {
                    continue;
                }
                JsonObject item = iv.asObject();
                KdbxDocument.KdbxEntry entry;
                try {
                    entry = parseItem(item, masterEnc, masterHmac, overviewEnc, overviewHmac);
                } catch (VaultReadException ex) {
                    // 单条失败不应阻断整库导入；保留结构化异常信息但继续后续条目
                    continue;
                }
                if (entry == null) {
                    continue;
                }
                String category = item.getString("category");
                String groupName = category == null ? "Unknown"
                        : CATEGORY_NAMES.getOrDefault(category, "Category " + category);
                KdbxDocument.KdbxGroup g = categoryGroups.computeIfAbsent(
                        category == null ? "Unknown" : category, c -> {
                            KdbxDocument.KdbxGroup ng = new KdbxDocument.KdbxGroup();
                            ng.name = groupName;
                            root.groups.add(ng);
                            return ng;
                        });
                g.entries.add(entry);
            }
        }

        // folders.js 即使为空也解析一次，避免未来非空时静默忽略（当前仅校验可解析）
        if (foldersJs != null) {
            parseJs(foldersJs, FOLDERS_PREFIX, ");");
        }

        return new KdbxDocument(root);
    }

    /** 解密单条目（k/o/d 均为 base64 字符串），映射为统一条目模型。 */
    private static KdbxDocument.KdbxEntry parseItem(JsonObject item,
            byte[] masterEnc, byte[] masterHmac, byte[] overviewEnc, byte[] overviewHmac)
            throws VaultReadException {
        String kB64 = item.getString("k");
        String oB64 = item.getString("o");
        String dB64 = item.getString("d");
        if (kB64 == null || oB64 == null || dB64 == null) {
            throw fail(Stage.STRUCTURE, "条目缺少 k/o/d 加密字段");
        }
        byte[] keyPlain = itemKeyDecrypt(kB64, masterEnc, masterHmac);
        byte[] itemEnc = slice(keyPlain, 0, 32);
        byte[] itemHmac = slice(keyPlain, 32, 32);

        byte[] overviewBytes = opdata01Decrypt(oB64, overviewEnc, overviewHmac);
        byte[] detailsBytes = opdata01Decrypt(dB64, itemEnc, itemHmac);
        JsonObject overview = parseJson(overviewBytes);
        JsonObject details = parseJson(detailsBytes);

        String title = overview.getString("title");
        String ainfo = overview.getString("ainfo");

        // 用户名 / 密码：优先取明细顶层 fields 中 designation 标记的，其次取 section 字段
        String username = null;
        String password = details.getString("password");
        JsonArray topFields = details.getArray("fields");
        if (topFields != null) {
            for (JsonValue fv : topFields.elements()) {
                if (!fv.isObject()) {
                    continue;
                }
                JsonObject f = fv.asObject();
                String designation = optString(f, "designation");
                String value = optString(f, "value");
                if ("username".equals(designation)) {
                    username = value;
                } else if ("password".equals(designation)) {
                    password = value;
                }
            }
        }

        // URL：概览 url，其次 URLs[]，再次 section 中的 url
        String url = overview.getString("url");
        if (url == null) {
            JsonArray urls = overview.getArray("URLs");
            if (urls != null) {
                for (JsonValue uv : urls.elements()) {
                    if (uv.isObject()) {
                        String u = uv.asObject().getString("u");
                        if (u != null) {
                            url = u;
                            break;
                        }
                    }
                }
            }
        }

        String notes = details.getString("notesPlain");
        String totp = null;

        // sections 中补充用户名/密码/URL 与 TOTP、其余作为自定义字段
        JsonArray sections = details.getArray("sections");
        if (sections != null) {
            for (JsonValue sv : sections.elements()) {
                if (!sv.isObject()) {
                    continue;
                }
                JsonObject section = sv.asObject();
                JsonArray sFields = section.getArray("fields");
                if (sFields == null) {
                    continue;
                }
                for (JsonValue fv : sFields.elements()) {
                    if (!fv.isObject()) {
                        continue;
                    }
                    JsonObject f = fv.asObject();
                    String n = optString(f, "n");
                    String t = optString(f, "t");
                    String v = optString(f, "v");
                    if (v == null) {
                        continue;
                    }
                    if ("one-time password".equals(t) || (v.startsWith("otpauth://") && totp == null)) {
                        if (totp == null) {
                            totp = v;
                        }
                        continue;
                    }
                    if (username == null && ("username".equals(n) || "username".equals(t))) {
                        username = v;
                        continue;
                    }
                    if (password == null && ("password".equals(n) || "password".equals(t))) {
                        password = v;
                        continue;
                    }
                    if (url == null && ("url".equals(n) || "URL".equals(t) || "website".equals(t))) {
                        url = v;
                        continue;
                    }
                    if ("username".equals(n) || "password".equals(n) || "url".equals(n) || "URL".equals(t)) {
                        continue; // 已映射到规范字段，避免重复
                    }
                    // 其余自定义字段在下方二次遍历收集
                }
            }
        }

        KdbxDocument.KdbxEntry entry = new KdbxDocument.KdbxEntry();
        entry.name = title == null ? "" : title;
        entry.uuid = item.getString("uuid");
        put(entry, "Title", title);
        put(entry, "UserName", username);
        put(entry, "Password", password);
        put(entry, "URL", url);
        put(entry, "Notes", notes);
        if (totp != null) {
            put(entry, "TOTP", totp);
        }
        if (ainfo != null && !ainfo.isEmpty() && !ainfo.equals(username)) {
            put(entry, "Account", ainfo);
        }

        // 二次遍历 sections 收集自定义字段（跳过已映射的规范字段与 TOTP）
        if (sections != null) {
            for (JsonValue sv : sections.elements()) {
                if (!sv.isObject()) {
                    continue;
                }
                JsonObject section = sv.asObject();
                JsonArray sFields = section.getArray("fields");
                if (sFields == null) {
                    continue;
                }
                for (JsonValue fv : sFields.elements()) {
                    if (!fv.isObject()) {
                        continue;
                    }
                    JsonObject f = fv.asObject();
                    String n = optString(f, "n");
                    String t = optString(f, "t");
                    String v = optString(f, "v");
                    if (v == null) {
                        continue;
                    }
                    if ("one-time password".equals(t) || v.startsWith("otpauth://")) {
                        continue;
                    }
                    if ("username".equals(n) || "password".equals(n) || "url".equals(n)
                            || "URL".equals(t) || "username".equals(t) || "password".equals(t)) {
                        continue;
                    }
                    String fieldName = (t != null && !t.isEmpty()) ? t : n;
                    if (fieldName != null && !fieldName.isEmpty()) {
                        put(entry, fieldName, v);
                    }
                }
            }
        }
        return entry;
    }

    // ====== 解密原语 ======

    /** PBKDF2-HMAC-SHA512 派生，产出指定字节数。 */
    private static byte[] pbkdf2HmacSha512(char[] password, byte[] salt, int iterations, int outBytes)
            throws VaultReadException {
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, outBytes * 8);
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw fail(Stage.KDF, "PBKDF2-SHA512 派生失败", e);
        }
    }

    /** 解密 opdata01（AES/CBC/NoPadding），返回真实明文（已去掉随机前缀）。 */
    private static byte[] opdata01Decrypt(String b64, byte[] enc, byte[] hmac) throws VaultReadException {
        byte[] data = Base64.getDecoder().decode(b64);
        if (data.length < 64 || !startsWith(data, "opdata01")) {
            throw fail(Stage.DECRYPT, "opdata01 魔数缺失或数据过短");
        }
        long length = readLe64(data, 8);
        byte[] iv = slice(data, 16, 16);
        byte[] hmacSig = slice(data, data.length - 32, 32);
        byte[] body = slice(data, 0, data.length - 32);
        verifyHmac(hmac, body, hmacSig);
        byte[] ciphertext = slice(data, 32, data.length - 32 - 32);
        byte[] decrypted = aesCbc(enc, iv, ciphertext, false);
        int randomPrefix = 16 - (int) (length % 16);
        if (randomPrefix == 0) {
            randomPrefix = 16;
        }
        if (randomPrefix + length > decrypted.length) {
            throw fail(Stage.DECRYPT, "opdata01 明文长度越界（数据损坏）");
        }
        return slice(decrypted, randomPrefix, (int) length);
    }

    /** 解密条目密钥 k（IV+AES/CBC/PKCS7(80B)+HMAC），返回 64B 明文（itemEnc‖itemHmac）。 */
    private static byte[] itemKeyDecrypt(String b64, byte[] enc, byte[] hmac) throws VaultReadException {
        byte[] data = Base64.getDecoder().decode(b64);
        if (data.length < 48) {
            throw fail(Stage.DECRYPT, "条目密钥太短");
        }
        byte[] iv = slice(data, 0, 16);
        byte[] ciphertext = slice(data, 16, data.length - 32);
        byte[] hmacSig = slice(data, data.length - 32, 32);
        byte[] body = slice(data, 0, data.length - 32);
        verifyHmac(hmac, body, hmacSig);
        // 条目密钥明文恰为 64 字节（4 个分组），不使用填充
        return aesCbc(enc, iv, ciphertext, false);
    }

    private static void verifyHmac(byte[] key, byte[] data, byte[] expected) throws VaultReadException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] actual = mac.doFinal(data);
            if (!MessageDigest.isEqual(actual, expected)) {
                throw fail(Stage.DECRYPT, "HMAC 校验失败（主密码错误或数据损坏）");
            }
        } catch (VaultReadException e) {
            throw e;
        } catch (Exception e) {
            throw fail(Stage.DECRYPT, "HMAC 计算失败", e);
        }
    }

    private static byte[] aesCbc(byte[] key, byte[] iv, byte[] ct, boolean pkcs7) throws VaultReadException {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/" + (pkcs7 ? "PKCS5Padding" : "NoPadding"));
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw fail(Stage.DECRYPT, "AES-CBC 解密失败（" + e.getClass().getSimpleName() + "）", e);
        }
    }

    private static byte[] sha512(byte[] data) throws VaultReadException {
        try {
            return MessageDigest.getInstance("SHA-512").digest(data);
        } catch (Exception e) {
            throw fail(Stage.DECRYPT, "SHA-512 计算失败", e);
        }
    }

    // ====== JSON / 工具 ======

    private static JsonObject parseJs(String text, String prefix, String suffix) throws VaultReadException {
        String json = stripJs(text, prefix, suffix);
        JsonObject obj = parseJson(json.getBytes(StandardCharsets.UTF_8));
        if (obj == null) {
            throw fail(Stage.STRUCTURE, "无法解析 " + prefix + " 包裹的 JSON");
        }
        return obj;
    }

    private static JsonObject parseJson(byte[] bytes) throws VaultReadException {
        try {
            JsonValue v = JsonParser.parse(new String(bytes, StandardCharsets.UTF_8));
            if (!v.isObject()) {
                throw fail(Stage.STRUCTURE, "期望 JSON 对象");
            }
            return v.asObject();
        } catch (VaultReadException e) {
            throw e;
        } catch (Exception e) {
            throw fail(Stage.STRUCTURE, "JSON 解析失败", e);
        }
    }

    private static String stripJs(String text, String prefix, String suffix) {
        String t = text.trim();
        if (prefix != null && t.startsWith(prefix)) {
            t = t.substring(prefix.length());
        }
        if (suffix != null && t.endsWith(suffix)) {
            t = t.substring(0, t.length() - suffix.length());
        }
        return t.trim();
    }

    private static void put(KdbxDocument.KdbxEntry entry, String key, String value) {
        if (value != null) {
            entry.fields.put(key, new KdbxDocument.KdbxField(value, false));
        }
    }

    /** 类型容忍的取值：字符串原样返回；数字/布尔转文本；对象/数组回退为 JSON 文本；缺失/空返回 null。 */
    private static String optString(JsonObject o, String key) {
        JsonValue v = o.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isString()) {
            return v.asString();
        }
        if (v.isNumber()) {
            return v.asNumber().toString();
        }
        if (v.isBool()) {
            return Boolean.toString(v.asBool());
        }
        return v.toJsonString();
    }

    private static boolean startsWith(byte[] data, String prefix) {
        if (data.length < prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (data[i] != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static long readLe64(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (b[off + i] & 0xffL) << (8 * i);
        }
        return v;
    }

    private static byte[] slice(byte[] b, int off, int len) {
        byte[] out = new byte[len];
        System.arraycopy(b, off, out, 0, len);
        return out;
    }

    private static byte[] readAll(ZipInputStream zis) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static VaultReadException fail(Stage stage, String message) {
        return VaultReadException.of(stage, VaultFormat.OPVAULT, message);
    }

    private static VaultReadException fail(Stage stage, String message, Throwable cause) {
        return new VaultReadException(stage, VaultFormat.OPVAULT, message, cause);
    }
}
