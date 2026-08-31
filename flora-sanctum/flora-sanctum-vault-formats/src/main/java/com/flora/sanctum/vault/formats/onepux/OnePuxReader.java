package com.flora.sanctum.vault.formats.onepux;

import com.flora.root.codec.json.JsonParser;
import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonValue;
import com.flora.sanctum.kdbx.KdbxDocument;
import com.flora.sanctum.vault.formats.VaultFormat;
import com.flora.sanctum.vault.formats.VaultReadException;
import com.flora.sanctum.vault.formats.VaultReadException.Stage;
import com.flora.sanctum.vault.formats.VaultReader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 1Password 1PUX 导出格式只读读取器（无加密，纯 JSON）。
 * <p>1PUX 是一个 ZIP，核心为 {@code export.data}（JSON），其余为附件。其层级为
 * {@code accounts → vaults → items}；每个条目含：
 * <ul>
 *   <li>{@code categoryUuid}：类别代码（001=Login 等），用于建分组；</li>
 *   <li>{@code overview}：概览（{@code title}、{@code urls:[{label,url}]}）；</li>
 *   <li>{@code details}：{@code loginFields:[{value,designation,...}]}（用户名/密码）、
 *       {@code notesPlain}、{@code sections:[{title,fields:[{title,value}]}]}。</li>
 * </ul>
 * 区段字段的 {@code value} 是形如 {@code {type: 实际值}} 的对象：字符串类型（totp/phone/string/concealed…）
 * 直接取值；{@code email} 取 {@code email_address}；{@code address} 等对象拼接各字符串子字段。
 * 概览与明细在部分导出中为 gzip+base64 字符串，本读取器对此做了兼容解码。</p>
 * <p>分组按 保险库(vault) → 类别(category) 两级嵌套，忠实反映 1PUX 的库结构。</p>
 */
public final class OnePuxReader implements VaultReader {

    /** 1Password 类别代码 → 友好分组名（未知回退为 "Category_<代码>"）。 */
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
        return VaultFormat.ONEPUX;
    }

    @Override
    public KdbxDocument read(byte[] data, char[] password, byte[] keyFile) throws VaultReadException {
        byte[] exportData = findExportData(data);
        if (exportData == null) {
            throw fail(Stage.STRUCTURE, "1PUX 缺少 export.data（不是有效的 1Password 1PUX 导出）");
        }
        JsonObject root = parseJson(exportData);
        JsonArray accounts = root.getArray("accounts");
        if (accounts == null) {
            throw fail(Stage.STRUCTURE, "1PUX export.data 缺少 accounts 数组");
        }

        KdbxDocument.KdbxGroup rootGroup = new KdbxDocument.KdbxGroup();
        rootGroup.name = "Imported";

        for (JsonValue av : accounts.elements()) {
            if (!av.isObject()) {
                continue;
            }
            JsonObject account = av.asObject();
            JsonArray vaults = account.getArray("vaults");
            if (vaults == null) {
                continue;
            }
            for (JsonValue vv : vaults.elements()) {
                if (!vv.isObject()) {
                    continue;
                }
                JsonObject vault = vv.asObject();
                String vaultName = "Vault";
                JsonObject vaultAttrs = vault.getObject("attrs");
                if (vaultAttrs != null) {
                    String n = optString(vaultAttrs, "name");
                    if (n != null) {
                        vaultName = n;
                    }
                }
                KdbxDocument.KdbxGroup vaultGroup = new KdbxDocument.KdbxGroup();
                vaultGroup.name = vaultName;
                rootGroup.groups.add(vaultGroup);

                Map<String, KdbxDocument.KdbxGroup> catGroups = new LinkedHashMap<>();
                JsonArray items = vault.getArray("items");
                if (items == null) {
                    continue;
                }
                for (JsonValue iv : items.elements()) {
                    if (!iv.isObject()) {
                        continue;
                    }
                    KdbxDocument.KdbxEntry entry;
                    try {
                        entry = parseItem(iv.asObject());
                    } catch (VaultReadException ex) {
                        continue; // 单条失败不阻断整库
                    }
                    if (entry == null) {
                        continue;
                    }
                    String cat = iv.asObject().getString("categoryUuid");
                    String catName = cat == null ? "Unknown"
                            : CATEGORY_NAMES.getOrDefault(cat, "Category " + cat);
                    KdbxDocument.KdbxGroup cg = catGroups.computeIfAbsent(
                            cat == null ? "Unknown" : cat, c -> {
                                KdbxDocument.KdbxGroup ng = new KdbxDocument.KdbxGroup();
                                ng.name = catName;
                                vaultGroup.groups.add(ng);
                                return ng;
                            });
                    cg.entries.add(entry);
                }
            }
        }
        return new KdbxDocument(rootGroup);
    }

    private static KdbxDocument.KdbxEntry parseItem(JsonObject item) throws VaultReadException {
        JsonObject overview = optObject(item, "overview");
        JsonObject details = optObject(item, "details");
        if (overview == null || details == null) {
            throw fail(Stage.STRUCTURE, "条目缺少 overview / details");
        }

        String title = optString(overview, "title");

        // 用户名 / 密码：优先取 loginFields 中 designation 标记
        String username = null;
        String password = null;
        JsonArray loginFields = details.getArray("loginFields");
        if (loginFields != null) {
            for (JsonValue lfv : loginFields.elements()) {
                if (!lfv.isObject()) {
                    continue;
                }
                JsonObject lf = lfv.asObject();
                String designation = optString(lf, "designation");
                String value = optString(lf, "value");
                if ("username".equals(designation)) {
                    username = value;
                } else if ("password".equals(designation)) {
                    password = value;
                }
            }
        }

        // URL：概览 urls[].url
        String url = null;
        JsonArray urls = overview.getArray("urls");
        if (urls != null) {
            for (JsonValue uv : urls.elements()) {
                if (uv.isObject()) {
                    String u = optString(uv.asObject(), "url");
                    if (u != null) {
                        url = u;
                        break;
                    }
                }
            }
        }

        String notes = optString(details, "notesPlain");
        String totp = null;

        // 区段字段
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
                    String fTitle = optString(f, "title");
                    JsonValue valueField = f.get("value");
                    String resolved = resolveValue(valueField);
                    if (resolved == null) {
                        continue;
                    }
                    // TOTP：value 为 {totp: "..."}
                    if (valueField.isObject() && valueField.asObject().containsKey("totp")) {
                        if (totp == null) {
                            totp = resolved;
                        }
                        continue;
                    }
                    // 兼容从区段补充用户名/密码
                    if (username == null && "username".equalsIgnoreCase(fTitle)) {
                        username = resolved;
                        continue;
                    }
                    if (password == null && "password".equalsIgnoreCase(fTitle)) {
                        password = resolved;
                        continue;
                    }
                    if (url == null && (fTitle != null && (fTitle.contains("url")
                            || fTitle.contains("URL")))) {
                        url = resolved;
                        continue;
                    }
                    // 其余作为自定义字段（在下方二次遍历统一收集）
                }
            }
        }

        KdbxDocument.KdbxEntry entry = new KdbxDocument.KdbxEntry();
        entry.name = title == null ? "" : title;
        entry.uuid = optString(item, "uuid");
        put(entry, "Title", title);
        put(entry, "UserName", username);
        put(entry, "Password", password);
        put(entry, "URL", url);
        put(entry, "Notes", notes);
        if (totp != null) {
            put(entry, "TOTP", totp);
        }

        // 二次遍历收集自定义字段（跳过已映射的规范字段与 TOTP）
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
                    String fTitle = optString(f, "title");
                    JsonValue valueField = f.get("value");
                    String resolved = resolveValue(valueField);
                    if (resolved == null || fTitle == null || fTitle.isEmpty()) {
                        continue;
                    }
                    if (valueField.isObject() && valueField.asObject().containsKey("totp")) {
                        continue;
                    }
                    if ("username".equalsIgnoreCase(fTitle) || "password".equalsIgnoreCase(fTitle)
                            || fTitle.contains("url") || fTitle.contains("URL")) {
                        continue;
                    }
                    put(entry, fTitle, resolved);
                }
            }
        }
        return entry;
    }

    // ====== value 解析 ======

    /** 解析 1PUX 区段字段的 value：{type: 实际值}；字符串/数字直接取值；email/address 特殊处理。 */
    private static String resolveValue(JsonValue v) {
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
        if (v.isObject()) {
            JsonObject o = v.asObject();
            for (String type : o.keySet()) {
                JsonValue inner = o.get(type);
                if (inner == null || inner.isNull()) {
                    return null;
                }
                if (inner.isString()) {
                    return inner.asString();
                }
                if (inner.isNumber()) {
                    return inner.asNumber().toString();
                }
                if (inner.isBool()) {
                    return Boolean.toString(inner.asBool());
                }
                if (inner.isObject()) {
                    JsonObject io = inner.asObject();
                    String ea = optString(io, "email_address");
                    if (ea != null) {
                        return ea;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (String k : io.keySet()) {
                        String sv = optString(io, k);
                        if (sv != null) {
                            if (sb.length() > 0) {
                                sb.append(", ");
                            }
                            sb.append(sv);
                        }
                    }
                    return sb.length() > 0 ? sb.toString() : null;
                }
                return inner.toJsonString();
            }
        }
        return null;
    }

    // ====== ZIP / JSON ======

    private static byte[] findExportData(byte[] data) throws VaultReadException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().endsWith("export.data")) {
                    return readAll(zis);
                }
            }
        } catch (Exception ex) {
            throw fail(Stage.STRUCTURE, "1PUX 不是合法 ZIP 或读取失败", ex);
        }
        return null;
    }

    private static JsonObject parseJson(byte[] bytes) throws VaultReadException {
        // 概览/明细在部分导出中为 gzip+base64 字符串，这里先尝试直接解析 JSON
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        try {
            JsonValue v = JsonParser.parse(text);
            if (!v.isObject()) {
                throw fail(Stage.STRUCTURE, "期望 JSON 对象");
            }
            return v.asObject();
        } catch (VaultReadException pe) {
            throw pe;
        } catch (Exception ex) {
            // 兼容 gzip+base64：尝试 base64 解码后 gzip 解压
            try {
                byte[] raw = java.util.Base64.getDecoder().decode(text);
                try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                    byte[] uncompressed = readAllFrom(gz);
                    JsonValue v = JsonParser.parse(new String(uncompressed, StandardCharsets.UTF_8));
                    if (!v.isObject()) {
                        throw fail(Stage.STRUCTURE, "解压后的内容不是 JSON 对象");
                    }
                    return v.asObject();
                }
            } catch (VaultReadException ve) {
                throw ve;
            } catch (Exception gex) {
                throw fail(Stage.STRUCTURE, "JSON 解析失败", ex);
            }
        }
    }

    private static JsonObject optObject(JsonObject o, String key) {
        JsonValue v = o.get(key);
        return (v != null && v.isObject()) ? v.asObject() : null;
    }

    private static void put(KdbxDocument.KdbxEntry entry, String key, String value) {
        if (value != null) {
            entry.fields.put(key, new KdbxDocument.KdbxField(value, false));
        }
    }

    /** 类型容忍取值（同 OPVault 处理：数字/布尔转文本，对象回退 JSON 文本）。 */
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

    private static byte[] readAll(ZipInputStream zis) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static byte[] readAllFrom(GZIPInputStream gz) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = gz.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static VaultReadException fail(Stage stage, String message) {
        return VaultReadException.of(stage, VaultFormat.ONEPUX, message);
    }

    private static VaultReadException fail(Stage stage, String message, Throwable cause) {
        return new VaultReadException(stage, VaultFormat.ONEPUX, message, cause);
    }
}
