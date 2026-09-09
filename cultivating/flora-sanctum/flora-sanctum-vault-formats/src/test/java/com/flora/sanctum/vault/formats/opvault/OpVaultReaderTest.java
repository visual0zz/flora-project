package com.flora.sanctum.vault.formats.opvault;

import com.flora.sanctum.kdbx.KdbxDocument;
import com.flora.sanctum.vault.formats.VaultFormatReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OPVault（1Password Vault）读取器测试：使用 KeePassXC 官方样例（密码 "a"）。
 * 样例以目录形式提供，测试时打包为 ZIP 字节后传入读取器，验证解密与字段映射。
 */
class OpVaultReaderTest {

    private static final String PASSWORD = "a";

    /** 样例中存在的 band 文件（十六进制编号）。 */
    private static final String[] BAND_FILES = {
            "band_1.js", "band_3.js", "band_4.js", "band_5.js", "band_A.js"
    };

    @Test
    void readSampleVault() throws Exception {
        byte[] zip = buildVaultZip();
        KdbxDocument doc = new OpVaultReader().read(zip, PASSWORD.toCharArray(), null);

        List<KdbxDocument.KdbxEntry> entries = allEntries(doc);
        // 样例含 7 条条目：Secure Note / Complex Password / KeePassXC / Server / Credit Card / Trashed / Expired
        assertEquals(7, entries.size());

        KdbxDocument.KdbxEntry kp = find(entries, "KeePassXC");
        assertNotNull(kp, "应存在 KeePassXC 登录条目");
        assertEquals("keepassxc", field(kp, "UserName"));
        assertEquals("opvault", field(kp, "Password"));
        assertEquals("https://www.keepassxc.org", field(kp, "URL"));
        assertEquals("KeePassXC Account", field(kp, "Notes"));
        // 登录条目的 TOTP 以裸 base32 种子形式存放
        assertEquals("JBSWY3DPEHPK3PXP", field(kp, "TOTP"));

        KdbxDocument.KdbxEntry cp = find(entries, "Complex Password");
        assertNotNull(cp, "应存在 Complex Password 条目");
        assertEquals("HfgcHjEL}iO}^3N!?*cv~O:9GJZQ0>oC", field(cp, "Password"));
        String totp = field(cp, "TOTP");
        assertNotNull(totp);
        assertTrue(totp.startsWith("otpauth://"), "Complex Password TOTP 应为完整 otpauth URI");
        assertTrue(totp.contains("digits=8"), "应为 8 位动态码");
        assertTrue(totp.contains("period=45"), "应为 45 秒周期");

        KdbxDocument.KdbxEntry server = find(entries, "KeePassXC Server");
        assertNotNull(server, "应存在 Server 条目");
        assertEquals("keepassxc", field(server, "UserName"));
        assertEquals("1234", field(server, "Password"));
        assertEquals("keepassxc.org", field(server, "URL"));

        KdbxDocument.KdbxEntry card = find(entries, "My Credit Card");
        assertNotNull(card, "应存在 Credit Card 条目");
        // 信用卡区段字段作为自定义字段导入
        assertNotNull(field(card, "number"), "信用卡号应作为自定义字段导入");

        KdbxDocument.KdbxEntry trashed = find(entries, "Trashed Password");
        assertNotNull(trashed, "应存在 Trashed Password 条目");
        assertEquals("G,wiG)V%n4@!c&Q", field(trashed, "Password"));
    }

    @Test
    void detectFormat() throws Exception {
        byte[] zip = buildVaultZip();
        assertEquals(com.flora.sanctum.vault.formats.VaultFormat.OPVAULT,
                VaultFormatReader.detect(zip));
    }

    private static final String RES_BASE = "com/flora/sanctum/vault/formats/opvault/default/";

    private static byte[] buildVaultZip() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            addEntry(zos, "default/profile.js", readResource(RES_BASE + "profile.js"));
            addEntry(zos, "default/folders.js", readResource(RES_BASE + "folders.js"));
            for (String band : BAND_FILES) {
                addEntry(zos, "default/" + band, readResource(RES_BASE + band));
            }
        }
        return bos.toByteArray();
    }

    private static void addEntry(ZipOutputStream zos, String name, byte[] data) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private static byte[] readResource(String path) throws Exception {
        try (InputStream in = OpVaultReaderTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "缺少测试资源：" + path);
            return in.readAllBytes();
        }
    }

    private static List<KdbxDocument.KdbxEntry> allEntries(KdbxDocument doc) {
        List<KdbxDocument.KdbxEntry> out = new ArrayList<>();
        collect(doc.root, out);
        return out;
    }

    private static void collect(KdbxDocument.KdbxGroup g, List<KdbxDocument.KdbxEntry> out) {
        out.addAll(g.entries);
        for (KdbxDocument.KdbxGroup c : g.groups) {
            collect(c, out);
        }
    }

    private static KdbxDocument.KdbxEntry find(List<KdbxDocument.KdbxEntry> entries, String title) {
        for (KdbxDocument.KdbxEntry e : entries) {
            if (title.equals(e.name)) {
                return e;
            }
        }
        return null;
    }

    private static String field(KdbxDocument.KdbxEntry e, String key) {
        KdbxDocument.KdbxField f = e.fields.get(key);
        return f == null ? null : f.value;
    }
}
