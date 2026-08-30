package com.flora.sanctum.core.io.importer.kdbx;

import com.flora.sanctum.core.io.importer.ImportException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用 KeePassXC 官方测试向量（tests/data/Kdbx4Basic.kdbx，主口令 "test"）做单向验证。
 * <p>该文件是真实 KeePassXC 导出的 KDBX 4.0（Argon2d + ChaCha20），用于验证解码器
 * 是否真正符合 KeePass/KeePassXC 格式（而非「自编码自解码」的循环一致性）。</p>
 */
class KdbxOfficialVectorTest {

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

    @Test
    void parsesRealKeePassXcFile() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/core/io/importer/kdbx/Kdbx4Basic.kdbx");
        KdbxDocument doc = KdbxParser.parse(data, "test".toCharArray(), null);

        assertNotNull(doc.root, "根分组为空");
        System.out.println("=== Format400.kdbx 解析结果 ===");
        printGroup(doc.root, 0);
        System.out.println("==============================");

        assertTrue(doc.countEntries() >= 1, "应至少解析出 1 个条目");
        assertTrue(doc.countGroups() >= 1, "应至少解析出 1 个分组");

        // 受保护字段必须解密为已知明文（KeePassXC 官方 Format400.kdbx 的 Password 为 "Format400"）。
        // 若内层流算法/密钥派生错误，会得到乱码而断言失败。
        KdbxDocument.KdbxField pwd = findFirstField(doc.root, "Password");
        assertNotNull(pwd, "应存在 Password 字段");
        System.out.println("首个 Password 明文 = " + pwd.value);
        assertEquals("Format400", pwd.value, "Password 未正确解密（内层流错误）");
    }

    @Test
    void wrongPasswordRejected() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/core/io/importer/kdbx/Kdbx4Basic.kdbx");
        assertThrows(ImportException.class,
                () -> KdbxParser.parse(data, " definitely-wrong ".toCharArray(), null),
                "错误密码应被拒绝");
    }

    private static KdbxDocument.KdbxField findFirstField(KdbxDocument.KdbxGroup g, String name) {
        for (KdbxDocument.KdbxEntry e : g.entries) {
            KdbxDocument.KdbxField f = e.fields.get(name);
            if (f != null) {
                return f;
            }
        }
        for (KdbxDocument.KdbxGroup c : g.groups) {
            KdbxDocument.KdbxField f = findFirstField(c, name);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    private static boolean isReadableText(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                return false; // 控制字符 → 乱码
            }
        }
        return true;
    }

    private static void printGroup(KdbxDocument.KdbxGroup g, int depth) {
        String indent = "  ".repeat(depth);
        System.out.println(indent + "[组] " + g.name);
        for (KdbxDocument.KdbxEntry e : g.entries) {
            System.out.println(indent + "  [条目] " + e.name);
            for (var en : e.fields.entrySet()) {
                String v = en.getValue().value;
                String shown = en.getValue().protectedValue ? "(保护) " + v : v;
                System.out.println(indent + "    " + en.getKey() + " = " + shown);
            }
        }
        for (KdbxDocument.KdbxGroup c : g.groups) {
            printGroup(c, depth + 1);
        }
    }
}
