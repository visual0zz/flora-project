package com.flora.sanctum.vault.formats.bitwarden;

import com.flora.root.codec.json.JsonParser;
import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonValue;
import com.flora.sanctum.kdbx.KdbxDocument;
import com.flora.sanctum.vault.formats.VaultFormat;
import com.flora.sanctum.vault.formats.VaultReadException;
import com.flora.sanctum.vault.formats.VaultReader;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bitwarden 明文 JSON 导出读取器（只读）。
 * <p>Bitwarden 的未加密导出为单一 JSON：顶层含 {@code items} 与 {@code folders} 数组；
 * 条目 {@code login} 子对象携带 username/password/uris/totp，另有 {@code notes} 与自定义
 * {@code fields}。本读取器仅支持未加密导出（{@code encrypted:false}）；加密导出需主密码派生
 * 密钥后 AES 解密，超出本只读导入范围，遇到时给出明确不支持提示。</p>
 * <p>输出统一映射为 {@link KdbxDocument}：每个 folder 对应一个分组，条目落入其分组（无分组则入根）。</p>
 */
public final class BitwardenReader implements VaultReader {

    @Override
    public VaultFormat format() {
        return VaultFormat.BITWARDEN;
    }

    @Override
    public KdbxDocument read(byte[] data, char[] password, byte[] keyFile) throws VaultReadException {
        JsonObject root;
        try {
            JsonValue v = JsonParser.parse(new String(data, StandardCharsets.UTF_8));
            if (!v.isObject()) {
                throw VaultReadException.of(VaultReadException.Stage.STRUCTURE, format(), "Bitwarden 导出不是合法 JSON 对象");
            }
            root = v.asObject();
        } catch (VaultReadException e) {
            throw e;
        } catch (Exception e) {
            throw VaultReadException.of(VaultReadException.Stage.STRUCTURE, format(), "Bitwarden JSON 解析失败", e);
        }

        Boolean encrypted = root.getBool("encrypted");
        if (Boolean.TRUE.equals(encrypted)) {
            throw VaultReadException.of(VaultReadException.Stage.UNSUPPORTED, format(),
                    "Bitwarden 加密导出需主密码派生密钥后解密，本只读导入暂不支持");
        }

        KdbxDocument.KdbxGroup rootGroup = new KdbxDocument.KdbxGroup();
        rootGroup.name = "Imported";

        Map<String, KdbxDocument.KdbxGroup> folderGroups = new LinkedHashMap<>();
        JsonArray folders = root.getArray("folders");
        if (folders != null) {
            for (JsonValue fv : folders.elements()) {
                if (!fv.isObject()) {
                    continue;
                }
                JsonObject f = fv.asObject();
                String id = str(f, "id");
                String name = str(f, "name");
                if (id == null) {
                    continue;
                }
                KdbxDocument.KdbxGroup g = new KdbxDocument.KdbxGroup();
                g.name = name == null ? "" : name;
                g.uuid = id;
                rootGroup.groups.add(g);
                folderGroups.put(id, g);
            }
        }

        JsonArray items = root.getArray("items");
        if (items != null) {
            for (JsonValue iv : items.elements()) {
                if (!iv.isObject()) {
                    continue;
                }
                KdbxDocument.KdbxEntry entry = parseItem(iv.asObject());
                String folderId = str(iv.asObject(), "folderId");
                KdbxDocument.KdbxGroup target = folderId == null ? null : folderGroups.get(folderId);
                (target == null ? rootGroup : target).entries.add(entry);
            }
        }

        return new KdbxDocument(rootGroup);
    }

    private static KdbxDocument.KdbxEntry parseItem(JsonObject item) {
        KdbxDocument.KdbxEntry e = new KdbxDocument.KdbxEntry();
        String title = str(item, "name");
        if (title != null) {
            e.name = title;
        }
        e.uuid = str(item, "id");

        JsonObject login = item.getObject("login");
        if (login != null) {
            put(e, "UserName", str(login, "username"));
            put(e, "Password", str(login, "password"));
            put(e, "TOTP", str(login, "totp"));
            JsonArray uris = login.getArray("uris");
            if (uris != null) {
                for (JsonValue u : uris.elements()) {
                    if (u.isObject()) {
                        String uri = str(u.asObject(), "uri");
                        if (uri != null && !uri.isEmpty()) {
                            put(e, "URL", uri);
                            break;
                        }
                    } else if (u.isString()) {
                        put(e, "URL", u.asString());
                        break;
                    }
                }
            }
        }

        put(e, "Notes", str(item, "notes"));

        JsonArray fields = item.getArray("fields");
        if (fields != null) {
            for (JsonValue fv : fields.elements()) {
                if (!fv.isObject()) {
                    continue;
                }
                JsonObject f = fv.asObject();
                String name = str(f, "name");
                String value = str(f, "value");
                if (name != null && !name.isEmpty()) {
                    put(e, name, value);
                }
            }
        }
        return e;
    }

    private static void put(KdbxDocument.KdbxEntry e, String key, String value) {
        if (value == null) {
            return;
        }
        e.fields.put(key, new KdbxDocument.KdbxField(value, false));
    }

    private static String str(JsonObject o, String key) {
        String s = o.getString(key);
        return s == null ? null : s;
    }
}
