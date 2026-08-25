package com.flora.sanctum.app.io.importer.kdbx;

import com.flora.sanctum.app.io.importer.ImportException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 针对 AES-KDF（KeePass 默认 KDF）导入路径的回归测试。
 * <p>样本 {@code AesKdf400.kdbx} 用 Python 按 KeePass 规范生成：KDBX 4.0、
 * AES-KDF + AES-256-CBC + ChaCha20 内层流，密码 "zz"，含一个受保护 Password 字段
 * 明文为 {@code aes-kdf-secret-123}。该文件无法被旧版代码（直接抛
 * "暂不支持 AES-KDF"）导入，用于验证 AES-KDF 实现。</p>
 */
class KdbxAesKdfTest {

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
    void parsesAesKdfFile() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/app/io/importer/kdbx/AesKdf400.kdbx");
        KdbxDocument doc = KdbxParser.parse(data, "zz".toCharArray(), null);

        assertNotNull(doc.root, "根分组为空");

        KdbxDocument.KdbxField pwd = findFirstField(doc.root, "Password");
        assertNotNull(pwd, "应存在 Password 字段");
        assertEquals("aes-kdf-secret-123", pwd.value, "AES-KDF 下受保护字段应正确解密");

        KdbxDocument.KdbxField title = findFirstField(doc.root, "Title");
        assertNotNull(title, "应存在 Title 字段");
        assertEquals("AES-KDF Entry", title.value);
    }

    @Test
    void wrongPasswordRejected() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/app/io/importer/kdbx/AesKdf400.kdbx");
        assertThrows(ImportException.class,
                () -> KdbxParser.parse(data, "wrong-password".toCharArray(), null),
                "错误密码应被拒绝（AES-KDF 下 HMAC 校验失败）");
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
}
