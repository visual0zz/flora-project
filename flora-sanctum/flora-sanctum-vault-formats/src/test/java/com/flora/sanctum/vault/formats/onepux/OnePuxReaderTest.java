package com.flora.sanctum.vault.formats.onepux;

import com.flora.sanctum.kdbx.KdbxDocument;
import com.flora.sanctum.vault.formats.VaultFormat;
import com.flora.sanctum.vault.formats.VaultFormatReader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1PUX 读取器测试：使用官方样例 {@code 1PasswordExport.1pux}（纯 JSON，无加密）。
 * 样例含 1 账户 / 2 保险库 / 9 条条目。
 */
class OnePuxReaderTest {

    private static final String RESOURCE = "com/flora/sanctum/vault/formats/onepux/1PasswordExport.1pux";

    @Test
    void readSampleExport() throws Exception {
        byte[] data = readResource();
        KdbxDocument doc = new OnePuxReader().read(data, null, null);

        List<KdbxDocument.KdbxEntry> entries = allEntries(doc);
        // Personal(8) + Shared(1) = 9
        assertEquals(9, entries.size());

        KdbxDocument.KdbxEntry login = find(entries, "Login");
        assertNotNull(login, "应存在 Login 条目");
        assertEquals("team@keepassxc.org", field(login, "UserName"));
        assertEquals("password", field(login, "Password"));
        assertEquals("Note to self", field(login, "Notes"));
        assertEquals("https://keepassxc.org", field(login, "URL"));
        assertEquals("DFDFDEF", field(login, "TOTP"));

        KdbxDocument.KdbxEntry cc = find(entries, "Credit Card");
        assertNotNull(cc, "应存在 Credit Card 条目");
        assertNotNull(field(cc, "number"), "信用卡号应作为自定义字段导入");

        KdbxDocument.KdbxEntry bank = find(entries, "Bank Account");
        assertNotNull(bank, "应存在 Bank Account 条目（位于 Shared 保险库）");
        assertNotNull(field(bank, "account number"), "银行账号应作为自定义字段导入");

        KdbxDocument.KdbxEntry note = find(entries, "Secure Note");
        assertNotNull(note, "应存在 Secure Note 条目");
        assertEquals("This is a note", field(note, "Notes"));
    }

    @Test
    void detectFormat() throws Exception {
        byte[] data = readResource();
        assertEquals(VaultFormat.ONEPUX, VaultFormatReader.detect(data));
    }

    private static byte[] readResource() throws Exception {
        try (InputStream in = OnePuxReaderTest.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "缺少测试资源：" + RESOURCE);
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
