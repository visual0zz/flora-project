package com.flora.sanctum.core.crypto;

import com.flora.root.crypto.Argon2;
import com.flora.sanctum.core.crypto.impl.SecureRandomSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Argon2 与 Argon2KDF 测试。
 * <p>KAT 值由替换前的 Bouncy Castle 参考实现生成，用于回归验证自研实现与其逐字节一致。</p>
 * <p>用例含多组真实 Argon2 计算（KAT 向量含高内存/多 lane 参数），整体耗时数秒，
 * 标记为 slow 以免拖慢常规测试；由 test-slow.cmd 覆盖。</p>
 */
@Tag("slow")
class Argon2KDFTest {

    private static final byte[] PWD = "password123".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SALT = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    @Test
    void digestMatchesBcReference() {
        assertEquals("e7a2e6593b28c0d663e64290e0194b02841265fd476fe14347af9efabac63db6",
                hex(Argon2.digest(PWD, SALT, 1024, 3, 4, 32)));
        assertEquals("cb5fa76a6cf590caf81182d1e404379944e99bfb6970969bdd9ec60afeb00fab",
                hex(Argon2.digest(PWD, SALT, 1024, 2, 1, 32)));
        assertEquals("31a059aa591eef1e0b5970134a1739139f8c4671326f4cf5fdefbbeda3fb5512",
                hex(Argon2.digest(PWD, SALT, 4096, 1, 2, 32)));
        assertEquals("94ee02c714e11ee78e9a7a00b1c8e1e9",
                hex(Argon2.digest(PWD, SALT, 64, 3, 4, 16)));
    }

    /**
     * 官方 KAT 向量（RustCrypto password-hashes argon2/tests/kat.rs，对齐 phc-winner-argon2 参考实现）：
     * 口令 = 32×0x01、盐 = 16×0x02、secret = 8×0x03、ad = 12×0x04、t=3、m=32(KiB)、p=4、输出 32 字节。
     * 覆盖 Argon2d/Argon2i/Argon2id 三种类型，重点验证此前未被 Bouncy Castle 对照的
     * Argon2d 数据相关寻址路径与多 lane 填充。
     */
    @Test
    void officialReferenceVectors() {
        byte[] pwd = new byte[32];
        byte[] salt = new byte[16];
        byte[] secret = new byte[8];
        byte[] ad = new byte[12];
        for (int i = 0; i < 32; i++) {
            pwd[i] = 0x01;
        }
        for (int i = 0; i < 16; i++) {
            salt[i] = 0x02;
        }
        for (int i = 0; i < 8; i++) {
            secret[i] = 0x03;
        }
        for (int i = 0; i < 12; i++) {
            ad[i] = 0x04;
        }
        assertAll(
                () -> assertEquals("512b391b6f1162975371d30919734294f868e3be3984f3c1a13a4db9fabe4acb",
                        hex(Argon2.digest(Argon2.TYPE_D, pwd, salt, secret, ad, 32, 3, 4, 32)), "Argon2d"),
                () -> assertEquals("c814d9d1dc7f37aa13f0d77f2494bda1c8de6b016dd388d29952a4c4672b6ce8",
                        hex(Argon2.digest(Argon2.TYPE_I, pwd, salt, secret, ad, 32, 3, 4, 32)), "Argon2i"),
                () -> assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659",
                        hex(Argon2.digest(Argon2.TYPE_ID, pwd, salt, secret, ad, 32, 3, 4, 32)), "Argon2id"));
    }

    @Test
    void deriveProduces32Bytes() {
        byte[] salt = new byte[16];
        new SecureRandomSource().nextBytes(salt);
        Argon2KDF kdf = new Argon2KDF(salt);
        byte[] kek = kdf.derive("password123".toCharArray());
        assertEquals(32, kek.length);
    }

    @Test
    void samePasswordAndSaltSameKek() {
        byte[] salt = new byte[16];
        new SecureRandomSource().nextBytes(salt);
        Argon2KDF kdf = new Argon2KDF(salt);
        assertArrayEquals(kdf.derive("pw".toCharArray()), kdf.derive("pw".toCharArray()));
    }

    @Test
    void differentPasswordDifferentKek() {
        byte[] salt = new byte[16];
        new SecureRandomSource().nextBytes(salt);
        Argon2KDF kdf = new Argon2KDF(salt);
        assertFalse(Arrays.equals(kdf.derive("a".toCharArray()), kdf.derive("b".toCharArray())));
    }

    private static String hex(byte[] out) {
        return HexFormat.of().formatHex(out);
    }
}
