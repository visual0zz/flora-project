package com.flora.sanctum.kdbx;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用 Python 自构造的 KDBX2/3 向量做端到端验证（口令 "test"，Password 明文 "s3cr3t"）。
 * <p>覆盖 KDBX2（AES-KDF + Salsa20 内层流）、KDBX3（压缩 / 非压缩）、以及 KDBX3 主密码+密钥文件组合。</p>
 * <p>自构造向量可验证解析器对 KDBX2/3 完整链路（2 字节头部字段、TransformSeed/Rounds、
 * StreamStartBytes 校验、HashedBlockStream、GZIP、外层头内层流）的正确性，而非仅「自编码自解码」。</p>
 */
class Kdbx23SelfVectorTest {

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
    void parsesKdbx2AesKdf() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/kdbx/Kdbx2AesKdfSelf.kdbx");
        KdbxDocument doc = KdbxReader.read(data, "test".toCharArray(), null);
        assertNotNull(doc.root);
        assertTrue(doc.countEntries() >= 1);
        assertEquals("s3cr3t", findFirstField(doc.root, "Password").value,
                "KDBX2 受保护密码未正确解密（Salsa20 内层流错误）");
        assertEquals("My Entry", findFirstField(doc.root, "Title").value);
        assertEquals("admin", findFirstField(doc.root, "UserName").value);
    }

    @Test
    void parsesKdbx3AesKdf() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/kdbx/Kdbx3AesKdfSelf.kdbx");
        KdbxDocument doc = KdbxReader.read(data, "test".toCharArray(), null);
        assertNotNull(doc.root);
        assertTrue(doc.countEntries() >= 1);
        assertEquals("s3cr3t", findFirstField(doc.root, "Password").value,
                "KDBX3 受保护密码未正确解密（Salsa20 内层流错误）");
        assertEquals("My Entry", findFirstField(doc.root, "Title").value);
        assertEquals("admin", findFirstField(doc.root, "UserName").value);
    }

    @Test
    void parsesKdbx3NoCompression() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/kdbx/Kdbx3NoCompression.kdbx");
        KdbxDocument doc = KdbxReader.read(data, "test".toCharArray(), null);
        assertNotNull(doc.root);
        assertTrue(doc.countEntries() >= 1);
        assertEquals("s3cr3t", findFirstField(doc.root, "Password").value,
                "KDBX3 非压缩载荷未正确解密");
    }

    @Test
    void parsesKdbx3WithKeyFile() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/kdbx/Kdbx3KeyFile.kdbx");
        byte[] keyFile = "flora-sanctum-keyfile-bytes-0123456789".getBytes(StandardCharsets.UTF_8);
        KdbxDocument doc = KdbxReader.read(data, "test".toCharArray(), keyFile);
        assertNotNull(doc.root);
        assertTrue(doc.countEntries() >= 1);
        assertEquals("s3cr3t", findFirstField(doc.root, "Password").value,
                "KDBX3 主密码+密钥文件组合未正确解密");
    }

    @Test
    void wrongPasswordRejectedKdbx2() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/kdbx/Kdbx2AesKdfSelf.kdbx");
        assertThrows(KdbxReadException.class,
                () -> KdbxReader.read(data, " definitely-wrong ".toCharArray(), null),
                "KDBX2 错误密码应被拒绝");
    }

    @Test
    void wrongPasswordRejectedKdbx3() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/kdbx/Kdbx3AesKdfSelf.kdbx");
        assertThrows(KdbxReadException.class,
                () -> KdbxReader.read(data, " definitely-wrong ".toCharArray(), null),
                "KDBX3 错误密码应被拒绝");
    }

    @Test
    void missingKeyFileRejected() throws Exception {
        // 该文件要求主密码 + 密钥文件，仅用主密码应失败。
        byte[] data = loadResource("/com/flora/sanctum/kdbx/Kdbx3KeyFile.kdbx");
        assertThrows(KdbxReadException.class,
                () -> KdbxReader.read(data, "test".toCharArray(), null),
                "缺少密钥文件应被拒绝");
    }
}
