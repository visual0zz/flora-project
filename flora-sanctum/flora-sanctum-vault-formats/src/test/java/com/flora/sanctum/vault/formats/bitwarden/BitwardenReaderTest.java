package com.flora.sanctum.vault.formats.bitwarden;

import com.flora.sanctum.kdbx.KdbxDocument;
import com.flora.sanctum.vault.formats.VaultFormat;
import com.flora.sanctum.vault.formats.VaultFormatReader;
import com.flora.sanctum.vault.formats.VaultReadException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bitwarden 明文导出向量测试。
 * <p>向量由 {@code absent/tmp/gen_bitwarden.py} 生成，覆盖：分组映射、login 字段、
 * uris 首个 URL、notes、自定义 fields、以及无 folderId 的条目落入根分组。</p>
 */
class BitwardenReaderTest {

    private static final String VECTOR = "/com/flora/sanctum/vault/formats/BitwardenExport.json";

    private byte[] loadResource(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(name)) {
            assertNotNull(in, "找不到测试向量资源: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private KdbxDocument read() throws Exception {
        return new BitwardenReader().read(loadResource(VECTOR), null, null);
    }

    private KdbxDocument.KdbxGroup group(KdbxDocument doc, String name) {
        for (KdbxDocument.KdbxGroup g : doc.root.groups) {
            if (name.equals(g.name)) {
                return g;
            }
        }
        fail("找不到分组: " + name);
        return null;
    }

    private KdbxDocument.KdbxEntry entry(KdbxDocument.KdbxGroup g, String title) {
        for (KdbxDocument.KdbxEntry e : g.entries) {
            if (title.equals(e.name)) {
                return e;
            }
        }
        fail("分组 " + g.name + " 中找不到条目: " + title);
        return null;
    }

    private String field(KdbxDocument.KdbxEntry e, String name) {
        KdbxDocument.KdbxField f = e.fields.get(name);
        return f == null ? null : f.value;
    }

    @Test
    void detectsBitwardenExport() throws Exception {
        assertEquals(VaultFormat.BITWARDEN, VaultFormatReader.detect(loadResource(VECTOR)));
    }

    @Test
    void mapsFoldersToGroups() throws Exception {
        KdbxDocument doc = read();
        assertNotNull(doc.root);
        assertEquals(3, doc.countGroups(), "根 + Work + Personal");
        assertEquals(3, doc.countEntries());
        assertEquals("Work", group(doc, "Work").name);
        assertEquals("Personal", group(doc, "Personal").name);
    }

    @Test
    void mapsLoginFieldsOfFolderedItem() throws Exception {
        KdbxDocument doc = read();
        KdbxDocument.KdbxEntry google = entry(group(doc, "Work"), "Google");
        assertEquals("alice@example.com", field(google, "UserName"));
        assertEquals("s3cr3t", field(google, "Password"));
        assertEquals("https://google.com", field(google, "URL"));
        assertEquals("backup@example.com", field(google, "RecoveryEmail"), "自定义 field 应按 name 映射");
        assertNotEquals("", field(google, "TOTP"));
        assertTrue(field(google, "TOTP").startsWith("otpauth://totp/"));
        assertEquals("work account notes", field(google, "Notes"));
        assertEquals("item-google", google.uuid);
    }

    @Test
    void mapsItemWithoutNotesAndUri() throws Exception {
        KdbxDocument doc = read();
        KdbxDocument.KdbxEntry bank = entry(group(doc, "Personal"), "Bank");
        assertEquals("bob", field(bank, "UserName"));
        assertEquals("hunter2", field(bank, "Password"));
        assertEquals("https://bank.example.org", field(bank, "URL"));
        assertFalse(bank.fields.containsKey("Notes"), "notes 为 null 时不应产出字段");
    }

    @Test
    void putsFolderlessItemIntoRootGroup() throws Exception {
        KdbxDocument doc = read();
        boolean inSubGroup = doc.root.groups.stream().flatMap(g -> g.entries.stream()).anyMatch(e -> "Loose Note Login".equals(e.name));
        assertFalse(inSubGroup, "无 folderId 的条目不应进入任何 folder 分组");
        KdbxDocument.KdbxEntry loose = entry(doc.root, "Loose Note Login");
        assertEquals("carol", field(loose, "UserName"));
        assertEquals("pw-carol", field(loose, "Password"));
        assertFalse(loose.fields.containsKey("URL"), "uris 为空时不应产出 URL 字段");
    }

    @Test
    void rejectsEncryptedExport() {
        byte[] encrypted = "{\"encrypted\":true,\"items\":[]}".getBytes(StandardCharsets.UTF_8);
        VaultReadException ex = assertThrows(VaultReadException.class,
                () -> new BitwardenReader().read(encrypted, null, null));
        assertEquals(VaultReadException.Stage.UNSUPPORTED, ex.stage());
        assertEquals(VaultFormat.BITWARDEN, ex.format());
    }

    @Test
    void rejectsMalformedJson() {
        byte[] broken = "{\"encrypted\":false, ".getBytes(StandardCharsets.UTF_8);
        VaultReadException ex = assertThrows(VaultReadException.class,
                () -> new BitwardenReader().read(broken, null, null));
        assertEquals(VaultReadException.Stage.STRUCTURE, ex.stage());
    }
}
