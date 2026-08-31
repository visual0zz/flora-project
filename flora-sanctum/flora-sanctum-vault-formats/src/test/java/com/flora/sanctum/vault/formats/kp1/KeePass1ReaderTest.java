package com.flora.sanctum.vault.formats.kp1;

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
 * KeePass1（KDB）读取器测试，向量取自 KeePassXC 官方测试数据（{@code tests/data}）。
 * <p>{@code basic.kdb} 口令为 "masterpw"；{@code FileKey*}.kdb 仅用同名密钥文件；
 * {@code CompositeKey.kdb} 为口令 "mypassword" + {@code FileKeyHex.key} 组合；
 * {@code Twofish.kdb} 为 Twofish 加密，用于验证明确的不支持提示。</p>
 */
class KeePass1ReaderTest {

    private byte[] load(String name) throws IOException {
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

    private byte[] kdb(String name) throws IOException {
        return load("/com/flora/sanctum/vault/formats/kp1/" + name);
    }

    private KdbxDocument read(String file, String password, String keyFileName) throws Exception {
        byte[] keyFile = keyFileName == null ? null : kdb(keyFileName);
        return new KeePass1Reader().read(kdb(file), password == null ? null : password.toCharArray(), keyFile);
    }

    private KdbxDocument.KdbxGroup child(KdbxDocument.KdbxGroup g, String name) {
        for (KdbxDocument.KdbxGroup c : g.groups) {
            if (name.equals(c.name)) {
                return c;
            }
        }
        fail("分组 " + g.name + " 中没有名为 " + name + " 的子分组");
        return null;
    }

    private KdbxDocument.KdbxEntry entry(KdbxDocument.KdbxGroup g, int index) {
        assertTrue(index < g.entries.size(), "分组 " + g.name + " 条目不足");
        return g.entries.get(index);
    }

    private String field(KdbxDocument.KdbxEntry e, String name) {
        KdbxDocument.KdbxField f = e.fields.get(name);
        return f == null ? null : f.value;
    }

    @Test
    void detectsKeePass1() throws Exception {
        assertEquals(VaultFormat.KEEPASS1, VaultFormatReader.detect(kdb("basic.kdb")));
    }

    @Test
    void parsesBasicDatabase() throws Exception {
        KdbxDocument doc = read("basic.kdb", "masterpw", null);
        assertNotNull(doc.root);
        assertEquals(2, doc.root.groups.size(), "根下应有 Internet 与 eMail 两个分组");

        KdbxDocument.KdbxGroup internet = child(doc.root, "Internet");
        assertEquals(1, internet.iconId);
        assertEquals(2, internet.groups.size(), "Internet 下应有 Subgroup 1 / Subgroup 2");
        assertEquals(2, internet.entries.size());

        KdbxDocument.KdbxEntry e1 = entry(internet, 0);
        assertEquals("Test entry", e1.name);
        assertEquals("I", field(e1, "UserName"));
        assertEquals("http://example.com/", field(e1, "URL"));
        assertEquals("secretpassword", field(e1, "Password"));
        assertEquals("Lorem ipsum\ndolor sit amet", field(e1, "Notes"));

        KdbxDocument.KdbxEntry e2 = entry(internet, 1);
        assertEquals("", e2.name, "空标题条目应解析为空串");

        KdbxDocument.KdbxGroup sub1 = child(internet, "Subgroup 1");
        assertEquals(1, sub1.groups.size());
        KdbxDocument.KdbxGroup unexpanded = child(sub1, "Unexpanded");
        assertEquals(1, unexpanded.groups.size());
        KdbxDocument.KdbxGroup abc = child(unexpanded, "abc");
        assertTrue(abc.groups.isEmpty());
        assertEquals(0, child(internet, "Subgroup 2").groups.size());

        KdbxDocument.KdbxGroup email = child(doc.root, "eMail");
        assertEquals(19, email.iconId);
        assertEquals(1, email.entries.size());
    }

    @Test
    void rejectsWrongPassword() {
        VaultReadException ex = assertThrows(VaultReadException.class,
                () -> read("basic.kdb", "definitely-wrong", null));
        assertEquals(VaultReadException.Stage.DECRYPT, ex.stage());
        assertEquals(VaultFormat.KEEPASS1, ex.format());
    }

    @Test
    void readsWithBinaryKeyFile() throws Exception {
        KdbxDocument doc = read("FileKeyBinary.kdb", null, "FileKeyBinary.key");
        assertEquals(1, doc.root.groups.size());
        assertEquals("FileKeyBinary", doc.root.groups.get(0).name);
    }

    @Test
    void readsWithHexKeyFile() throws Exception {
        KdbxDocument doc = read("FileKeyHex.kdb", null, "FileKeyHex.key");
        assertEquals(1, doc.root.groups.size());
        assertEquals("FileKeyHex", doc.root.groups.get(0).name);
    }

    @Test
    void readsWithHashedKeyFile() throws Exception {
        KdbxDocument doc = read("FileKeyHashed.kdb", null, "FileKeyHashed.key");
        assertEquals(1, doc.root.groups.size());
        assertEquals("FileKeyHashed", doc.root.groups.get(0).name);
    }

    @Test
    void readsWithCompositeKey() throws Exception {
        KdbxDocument doc = read("CompositeKey.kdb", "mypassword", "FileKeyHex.key");
        assertEquals(1, doc.root.groups.size());
        assertEquals("CompositeKey", doc.root.groups.get(0).name);
    }

    @Test
    void rejectsMissingKeyFile() {
        assertThrows(VaultReadException.class, () -> read("FileKeyBinary.kdb", null, null));
    }

    @Test
    void rejectsTwofishAsUnsupported() {
        VaultReadException ex = assertThrows(VaultReadException.class,
                () -> read("Twofish.kdb", "masterpw", null));
        assertEquals(VaultReadException.Stage.UNSUPPORTED, ex.stage());
        assertTrue(ex.getMessage().contains("Twofish"), "应给出 Twofish 不支持的明确提示");
    }

    /** 历史文件以 Windows-1252 保存口令：口令为 „password”（U+201E/U+201D 引号），UTF-8 编码不通，需回退。 */
    @Test
    void readsCp1252EncodedPassword() throws Exception {
        KdbxDocument doc = read("CP-1252.kdb", "„password”", null);
        assertEquals(1, doc.root.groups.size());
        assertEquals("CP-1252", doc.root.groups.get(0).name);
    }

    @Test
    void rejectsTruncatedFile() throws Exception {
        byte[] head = new byte[40];
        System.arraycopy(kdb("basic.kdb"), 0, head, 0, 40);
        VaultReadException ex = assertThrows(VaultReadException.class,
                () -> new KeePass1Reader().read(head, "masterpw".toCharArray(), null));
        assertEquals(VaultReadException.Stage.MAGIC, ex.stage());
    }

    @Test
    void rejectsNonKeePassData() {
        byte[] junk = "not a database at all, just some plain text bytes here".getBytes(StandardCharsets.UTF_8);
        VaultReadException ex = assertThrows(VaultReadException.class,
                () -> new KeePass1Reader().read(junk, "masterpw".toCharArray(), null));
        assertEquals(VaultReadException.Stage.MAGIC, ex.stage());
    }
}
