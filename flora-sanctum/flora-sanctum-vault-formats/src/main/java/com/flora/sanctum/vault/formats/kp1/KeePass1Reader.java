package com.flora.sanctum.vault.formats.kp1;

import com.flora.root.crypto.AesKdf;
import com.flora.sanctum.kdbx.KdbxDocument;
import com.flora.sanctum.vault.formats.VaultFormat;
import com.flora.sanctum.vault.formats.VaultReadException;
import com.flora.sanctum.vault.formats.VaultReadException.Stage;
import com.flora.sanctum.vault.formats.VaultReader;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * KeePass 1.x（KDB）数据库读取器（只读）。
 * <p>与 KDBX 的关键差异：KDB1 没有内层随机流，正文经外层 AES/Twofish-CBC 解密后即为明文 TLV
 * 结构；完整性由头部的「正文 SHA-256」单独校验，而非 KDBX 的 StreamStartBytes / HMAC。</p>
 * <p>头部固定 124 字节，其后是密文；正文先连续存放 numGroups 个分组、再连续存放 numEntries 个条目，
 * 二者均由 [2 字节类型][4 字节长度][数据] 的 TLV 组成，以类型 {@code 0xFFFF} 结束。分组树由分组的
 * Level 字段重建（向前找首个层级恰好小 1 的分组作为父分组）。</p>
 * <p>限制：仅实现 Rijndael(AES-256)，Twofish 加密的文件明确报 UNSUPPORTED；
 * KDBX 共用的 {@link KdbxDocument} 模型不承载附件与自定义图标，故 KDB1 的二进制附件与
 * Meta-Stream 图标数据不导入；Meta-Info 系统条目不出现在结果中。</p>
 */
public final class KeePass1Reader implements VaultReader {

    /** KeePass1 签名 2 为 {@code 0xB54BFB65}；KDBX（KeePass2）为 {@code 0xB54BFB67}，据此区分两种格式。 */
    private static final int SIG1 = 0x9AA2D903;
    private static final int SIG2 = 0xB54BFB65;
    private static final int VERSION_CRITICAL = 0x00030000;
    private static final int VERSION_CRITICAL_MASK = 0xFFFFFF00;
    private static final int HEADER_SIZE = 124;
    private static final int FLAG_RIJNDAEL = 2;
    private static final int FLAG_TWOFISH = 8;

    /** 分组字段类型。 */
    private static final int GROUP_ID = 0x0001;
    private static final int GROUP_NAME = 0x0002;
    private static final int GROUP_CREATION = 0x0003;
    private static final int GROUP_LAST_MOD = 0x0004;
    private static final int GROUP_ICON = 0x0007;
    private static final int GROUP_LEVEL = 0x0008;

    /** 条目字段类型。 */
    private static final int ENTRY_UUID = 0x0001;
    private static final int ENTRY_GROUP_ID = 0x0002;
    private static final int ENTRY_ICON = 0x0003;
    private static final int ENTRY_TITLE = 0x0004;
    private static final int ENTRY_URL = 0x0005;
    private static final int ENTRY_USERNAME = 0x0006;
    private static final int ENTRY_PASSWORD = 0x0007;
    private static final int ENTRY_NOTES = 0x0008;
    private static final int ENTRY_CREATION = 0x0009;
    private static final int ENTRY_LAST_MOD = 0x000A;

    private static final int FIELD_END = 0xFFFF;

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    @Override
    public VaultFormat format() {
        return VaultFormat.KEEPASS1;
    }

    @Override
    public KdbxDocument read(byte[] data, char[] password, byte[] keyFile) throws VaultReadException {
        if (data == null || data.length < HEADER_SIZE + 16) {
            throw fail(Stage.MAGIC, "文件过短，不是合法的 KeePass1 数据库");
        }
        if (u32(data, 0) != SIG1 || u32(data, 4) != SIG2) {
            throw fail(Stage.MAGIC, "KeePass1 魔数不匹配（签名应为 9AA2D903 B54BFB65）");
        }
        int flags = u32(data, 8);
        int version = u32(data, 12);
        if ((version & VERSION_CRITICAL_MASK) != VERSION_CRITICAL) {
            throw fail(Stage.HEADER, "不支持的 KeePass1 版本：0x" + Integer.toHexString(version));
        }
        byte[] masterSeed = slice(data, 16, 16);
        byte[] encryptionIv = slice(data, 32, 16);
        long numGroups = u32(data, 48) & 0xFFFFFFFFL;
        long numEntries = u32(data, 52) & 0xFFFFFFFFL;
        byte[] contentHash = slice(data, 56, 32);
        byte[] transformSeed = slice(data, 88, 32);
        long transformRounds = u32(data, 120) & 0xFFFFFFFFL;

        if ((flags & FLAG_TWOFISH) != 0) {
            throw fail(Stage.UNSUPPORTED, "KeePass1 数据库使用 Twofish 加密，本读取器暂不支持");
        }
        if ((flags & FLAG_RIJNDAEL) == 0) {
            throw fail(Stage.UNSUPPORTED, "未知的 KeePass1 加密算法（flags=0x" + Integer.toHexString(flags) + "）");
        }

        byte[] keyFileData = keyFileRaw(keyFile);
        boolean hasPassword = password != null && password.length > 0;
        if (!hasPassword && keyFileData == null) {
            throw fail(Stage.KDF, "至少需要主密码或密钥文件");
        }
        List<byte[]> encodings = hasPassword
                ? passwordEncodings(password)
                : java.util.Collections.singletonList(null);

        // 口令编码不确定（历史文件用 Windows-1252 / Latin-1，新文件用 UTF-8），逐个尝试至正文哈希校验通过。
        VaultReadException firstFailure = null;
        for (byte[] passwordBytes : encodings) {
            try {
                KdbxDocument doc = attemptRead(data, masterSeed, encryptionIv, transformSeed, transformRounds,
                        contentHash, numGroups, numEntries, passwordBytes, keyFileData);
                if (doc != null) {
                    return doc;
                }
            } catch (VaultReadException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        throw fail(Stage.DECRYPT, "主密码错误或文件已损坏（正文 SHA-256 与头部记录不一致）", firstFailure);
    }

    /** 用一种口令编码尝试解密并解析；正文哈希不匹配时返回 null（换下一种编码重试）。 */
    private KdbxDocument attemptRead(byte[] data, byte[] masterSeed, byte[] encryptionIv,
            byte[] transformSeed, long transformRounds, byte[] contentHash,
            long numGroups, long numEntries, byte[] passwordBytes, byte[] keyFileData)
            throws VaultReadException {
        byte[] composite;
        if (passwordBytes == null) {
            composite = keyFileData; // 仅密钥文件
        } else if (keyFileData == null) {
            composite = sha256(passwordBytes); // 仅主密码
        } else {
            composite = sha256(concat(sha256(passwordBytes), keyFileData));
        }
        byte[] transformed;
        try {
            transformed = AesKdf.transform(composite, transformSeed, transformRounds);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw fail(Stage.KDF, "密钥派生失败：" + e.getMessage(), e);
        }
        byte[] finalKey = AesKdf.finalKey(masterSeed, transformed);
        byte[] plain;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(finalKey, "AES"),
                    new IvParameterSpec(encryptionIv));
            plain = cipher.doFinal(data, HEADER_SIZE, data.length - HEADER_SIZE);
        } catch (Exception e) {
            throw fail(Stage.DECRYPT, "正文解密失败（" + e.getClass().getSimpleName() + "）", e);
        }
        return parseBody(plain, numGroups, numEntries, contentHash);
    }

    /** 解析解密后的正文；哈希不匹配返回 null，结构非法抛异常。 */
    private KdbxDocument parseBody(byte[] plain, long numGroups, long numEntries, byte[] contentHash)
            throws VaultReadException {
        Cursor cursor = new Cursor(plain);
        List<RawGroup> rawGroups = new ArrayList<>();
        for (long i = 0; i < numGroups; i++) {
            rawGroups.add(readGroup(cursor));
        }
        List<RawEntry> rawEntries = new ArrayList<>();
        for (long i = 0; i < numEntries; i++) {
            rawEntries.add(readEntry(cursor));
        }
        if (!MessageDigest.isEqual(sha256(plain, 0, cursor.pos), contentHash)) {
            return null;
        }
        return buildDocument(rawGroups, rawEntries);
    }

    private RawGroup readGroup(Cursor c) throws VaultReadException {
        RawGroup g = new RawGroup();
        boolean hasId = false;
        boolean hasLevel = false;
        while (true) {
            int type = c.u16();
            // 结束标记为 [0xFFFF][长度=0]，长度字段同样占位，必须先读再判断
            int size = c.u32();
            if (type == FIELD_END) {
                break;
            }
            byte[] value = c.bytes(size);
            switch (type) {
                case GROUP_ID -> {
                    require(value.length == 4, "分组 ID 字段长度应为 4");
                    g.id = u32(value, 0) & 0xFFFFFFFFL;
                    hasId = true;
                }
                case GROUP_NAME -> g.name = utf8(value);
                case GROUP_CREATION -> g.creation = packedTime(value);
                case GROUP_LAST_MOD -> g.lastMod = packedTime(value);
                case GROUP_ICON -> g.icon = value.length == 4 ? (int) u32(value, 0) : null;
                case GROUP_LEVEL -> {
                    require(value.length == 2, "分组层级字段长度应为 2");
                    g.level = u16(value, 0);
                    hasLevel = true;
                }
                default -> {
                    // 其余字段（访问时间、过期时间、标志位）导入时不需要
                }
            }
        }
        if (!hasId) {
            throw fail(Stage.STRUCTURE, "分组缺少 ID 字段");
        }
        if (!hasLevel) {
            throw fail(Stage.STRUCTURE, "分组缺少层级（Level）字段");
        }
        return g;
    }

    private RawEntry readEntry(Cursor c) throws VaultReadException {
        RawEntry e = new RawEntry();
        boolean hasGroupId = false;
        while (true) {
            int type = c.u16();
            int size = c.u32();
            if (type == FIELD_END) {
                break;
            }
            byte[] value = c.bytes(size);
            switch (type) {
                case ENTRY_UUID -> {
                    require(value.length == 16, "条目 UUID 字段长度应为 16");
                    e.uuid = hex(value);
                }
                case ENTRY_GROUP_ID -> {
                    require(value.length == 4, "条目所属分组字段长度应为 4");
                    e.groupId = u32(value, 0) & 0xFFFFFFFFL;
                    hasGroupId = true;
                }
                case ENTRY_ICON -> e.icon = value.length == 4 ? (int) u32(value, 0) : null;
                case ENTRY_TITLE -> e.title = utf8(value);
                case ENTRY_URL -> e.url = utf8(value);
                case ENTRY_USERNAME -> e.username = utf8(value);
                case ENTRY_PASSWORD -> e.password = utf8(value);
                case ENTRY_NOTES -> e.notes = utf8(value);
                case ENTRY_CREATION -> e.creation = packedTime(value);
                case ENTRY_LAST_MOD -> e.lastMod = packedTime(value);
                default -> {
                    // 附件（0x000D/0x000E）与访问时间等：KdbxDocument 不承载，忽略
                }
            }
        }
        if (!hasGroupId) {
            throw fail(Stage.STRUCTURE, "条目缺少所属分组 ID 字段");
        }
        return e;
    }

    /** 按 Level 重建分组树，并挂接条目。 */
    private KdbxDocument buildDocument(List<RawGroup> rawGroups, List<RawEntry> rawEntries)
            throws VaultReadException {
        KdbxDocument.KdbxGroup root = new KdbxDocument.KdbxGroup();
        root.name = "Root";
        List<KdbxDocument.KdbxGroup> nodes = new ArrayList<>();
        for (RawGroup rg : rawGroups) {
            KdbxDocument.KdbxGroup g = new KdbxDocument.KdbxGroup();
            g.name = rg.name == null ? "" : rg.name;
            g.uuid = Long.toHexString(rg.id);
            g.iconId = rg.icon;
            nodes.add(g);
        }
        for (int i = 0; i < nodes.size(); i++) {
            int level = rawGroups.get(i).level;
            if (level == 0) {
                root.groups.add(nodes.get(i));
                continue;
            }
            KdbxDocument.KdbxGroup parent = null;
            for (int j = i - 1; j >= 0; j--) {
                int prevLevel = rawGroups.get(j).level;
                if (prevLevel < level) {
                    if (level - prevLevel != 1) {
                        throw fail(Stage.STRUCTURE, "分组层级不连续（" + prevLevel + " → " + level + "）");
                    }
                    parent = nodes.get(j);
                    break;
                }
            }
            if (parent == null) {
                throw fail(Stage.STRUCTURE, "找不到层级为 " + (level - 1) + " 的父分组");
            }
            parent.groups.add(nodes.get(i));
        }

        java.util.Map<Long, KdbxDocument.KdbxGroup> byId = new java.util.HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            byId.put(rawGroups.get(i).id, nodes.get(i));
        }
        for (RawEntry re : rawEntries) {
            if (isMetaStream(re)) {
                continue; // Meta-Info 系统条目承载分组展开状态/自定义图标，不是真实条目
            }
            KdbxDocument.KdbxEntry entry = new KdbxDocument.KdbxEntry();
            entry.name = re.title == null ? "" : re.title;
            entry.uuid = re.uuid;
            entry.iconId = re.icon;
            entry.creationTime = re.creation;
            entry.lastModificationTime = re.lastMod;
            put(entry, "Title", re.title);
            put(entry, "UserName", re.username);
            put(entry, "Password", re.password);
            put(entry, "URL", re.url);
            put(entry, "Notes", re.notes);
            KdbxDocument.KdbxGroup group = byId.get(re.groupId);
            (group == null ? root : group).entries.add(entry);
        }
        return new KdbxDocument(root);
    }

    private static boolean isMetaStream(RawEntry e) {
        return "Meta-Info".equals(e.title) && "SYSTEM".equals(e.username) && "$".equals(e.url);
    }

    private static void put(KdbxDocument.KdbxEntry entry, String key, String value) {
        if (value != null) {
            entry.fields.put(key, new KdbxDocument.KdbxField(value, false));
        }
    }

    /** 密钥文件原始密钥：32 字节直接用；64 字节且全为十六进制则解码；其余取 SHA-256。 */
    private static byte[] keyFileRaw(byte[] keyFile) {
        if (keyFile == null || keyFile.length == 0) {
            return null;
        }
        if (keyFile.length == 32) {
            return keyFile;
        }
        if (keyFile.length == 64 && isHex(keyFile)) {
            return hexDecode(keyFile);
        }
        return sha256(keyFile);
    }

    private static boolean isHex(byte[] b) {
        for (byte v : b) {
            boolean hex = (v >= '0' && v <= '9') || (v >= 'a' && v <= 'f') || (v >= 'A' && v <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static byte[] hexDecode(byte[] b) {
        byte[] out = new byte[b.length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) ((hexDigit(b[i * 2]) << 4) | hexDigit(b[i * 2 + 1]));
        }
        return out;
    }

    private static int hexDigit(byte b) {
        if (b >= '0' && b <= '9') {
            return b - '0';
        }
        if (b >= 'a' && b <= 'f') {
            return b - 'a' + 10;
        }
        return b - 'A' + 10;
    }

    /** 历史文件以 Windows-1252 / Latin-1 保存口令，新文件用 UTF-8；去重后按此顺序尝试。 */
    private static List<byte[]> passwordEncodings(char[] password) {
        List<byte[]> out = new ArrayList<>();
        for (Charset cs : List.of(StandardCharsets.UTF_8, WINDOWS_1252, StandardCharsets.ISO_8859_1)) {
            byte[] bytes = new String(password).getBytes(cs);
            boolean duplicate = false;
            for (byte[] existing : out) {
                if (Arrays.equals(existing, bytes)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                out.add(bytes);
            }
        }
        return out;
    }

    /** KDB1 字符串字段以 NUL 结尾，解码前去掉尾部 NUL。 */
    private static String utf8(byte[] value) {
        int len = value.length;
        if (len > 0 && value[len - 1] == 0) {
            len--;
        }
        return new String(value, 0, len, StandardCharsets.UTF_8);
    }

    /** 5 字节打包日期 → epoch 毫秒；2999-12-28 23:59:59 表示「永不」，返回 null。 */
    private static Long packedTime(byte[] v) {
        if (v == null || v.length != 5) {
            return null;
        }
        int year = ((v[0] & 0xff) << 6) | ((v[1] & 0xff) >> 2);
        int month = (((v[1] & 0xff) & 0x03) << 2) | ((v[2] & 0xff) >> 6);
        int day = ((v[2] & 0xff) >> 1) & 0x1F;
        int hour = (((v[2] & 0xff) & 0x01) << 4) | ((v[3] & 0xff) >> 4);
        int minute = (((v[3] & 0xff) & 0x0F) << 2) | ((v[4] & 0xff) >> 6);
        int second = (v[4] & 0xff) & 0x3F;
        if (year == 2999 && month == 12 && day == 28 && hour == 23 && minute == 59 && second == 59) {
            return null;
        }
        try {
            return LocalDateTime.of(year, month, day, hour, minute, second)
                    .toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static void require(boolean condition, String message) throws VaultReadException {
        if (!condition) {
            throw fail(Stage.STRUCTURE, message);
        }
    }

    private static VaultReadException fail(Stage stage, String message) {
        return VaultReadException.of(stage, VaultFormat.KEEPASS1, message);
    }

    private static VaultReadException fail(Stage stage, String message, Throwable cause) {
        return new VaultReadException(stage, VaultFormat.KEEPASS1, message, cause);
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    private static int u32(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    private static byte[] slice(byte[] b, int off, int len) {
        byte[] out = new byte[len];
        System.arraycopy(b, off, out, 0, len);
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte v : b) {
            sb.append(Character.forDigit((v >> 4) & 0xF, 16)).append(Character.forDigit(v & 0xF, 16));
        }
        return sb.toString();
    }

    private static byte[] sha256(byte[] data) {
        return sha256(data, 0, data.length);
    }

    private static byte[] sha256(byte[] data, int off, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(data, off, len);
            return md.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("缺少 SHA-256 实现", e);
        }
    }

    /** 正文游标，越界即视为结构非法。 */
    private static final class Cursor {
        private final byte[] data;
        private int pos;

        Cursor(byte[] data) {
            this.data = data;
        }

        int u16() throws VaultReadException {
            need(2);
            int v = KeePass1Reader.u16(data, pos);
            pos += 2;
            return v;
        }

        int u32() throws VaultReadException {
            need(4);
            int v = KeePass1Reader.u32(data, pos);
            pos += 4;
            return v;
        }

        byte[] bytes(int len) throws VaultReadException {
            if (len < 0) {
                throw fail(Stage.STRUCTURE, "字段长度非法：" + len);
            }
            need(len);
            byte[] out = slice(data, pos, len);
            pos += len;
            return out;
        }

        private void need(int len) throws VaultReadException {
            if (len > data.length - pos) {
                throw fail(Stage.STRUCTURE, "正文结构截断（需要 " + len + " 字节）");
            }
        }
    }

    private static final class RawGroup {
        private long id;
        private String name;
        private Integer icon;
        private int level;
        private Long creation;
        private Long lastMod;
    }

    private static final class RawEntry {
        private String uuid;
        private long groupId;
        private Integer icon;
        private String title;
        private String url;
        private String username;
        private String password;
        private String notes;
        private Long creation;
        private Long lastMod;
    }
}
