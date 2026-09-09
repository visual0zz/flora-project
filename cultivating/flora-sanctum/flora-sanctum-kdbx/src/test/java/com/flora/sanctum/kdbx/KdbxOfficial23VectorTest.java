package com.flora.sanctum.kdbx;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用 KeePassXC 官方 KDBX2/3 向量（Format200 / Format300）做真实跨实现验证。
 * <p>Format300（KDBX3.1，口令 "a"）：条目 Sample Entry 的密码为 "Password"。
 * Format200（KDBX2.0，口令 "a"）：验证可完整解析（内层流通常为 Arc4 id=1）。</p>
 */
class KdbxOfficial23VectorTest {

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

    private KdbxDocument.KdbxField findFirstField(KdbxDocument.KdbxGroup g, String name) {
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

    @Test
    void parsesOfficialKdbx3() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/kdbx/Kdbx3AesKdf.kdbx");
        KdbxDocument doc = KdbxReader.read(data, "a".toCharArray(), null);
        assertNotNull(doc.root);
        assertTrue(doc.countEntries() >= 1);
        KdbxDocument.KdbxField pwd = findFirstField(doc.root, "Password");
        assertNotNull(pwd, "官方 KDBX3 文件应存在 Password 字段");
        assertEquals("Password", pwd.value, "官方 KDBX3 受保护密码未正确解密");
    }

    @Test
    void wrongPasswordRejectedOfficialKdbx3() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/kdbx/Kdbx3AesKdf.kdbx");
        assertThrows(KdbxReadException.class,
                () -> KdbxReader.read(data, "wrong-password".toCharArray(), null),
                "官方 KDBX3 错误密码应被拒绝");
    }

    @Test
    void parsesOfficialKdbx2() throws Exception {
        // KDBX2.0 默认内层流通常为 Arc4（id=1），验证本解析器可解密。
        byte[] data = loadResource("/com/flora/sanctum/kdbx/Kdbx2AesKdf.kdbx");
        KdbxDocument doc = KdbxReader.read(data, "a".toCharArray(), null);
        assertNotNull(doc.root, "官方 KDBX2 文件应成功解析");
        assertTrue(doc.countEntries() >= 1, "官方 KDBX2 文件应至少含 1 个条目");
        System.out.println("=== 官方 KDBX2 (Format200) 解析 ===");
        System.out.println("根分组名: " + doc.root.name);
        System.out.println("条目数: " + doc.countEntries());
        KdbxDocument.KdbxField pwd = findFirstField(doc.root, "Password");
        if (pwd != null) {
            System.out.println("Password 明文 = " + pwd.value);
        }
    }

    @Test
    void wrongPasswordRejectedOfficialKdbx2() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/kdbx/Kdbx2AesKdf.kdbx");
        assertThrows(KdbxReadException.class,
                () -> KdbxReader.read(data, "wrong-password".toCharArray(), null),
                "官方 KDBX2 错误密码应被拒绝");
    }
}
