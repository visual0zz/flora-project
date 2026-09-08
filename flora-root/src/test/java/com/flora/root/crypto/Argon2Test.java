package com.flora.root.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Argon2 已知答案测试（小参数，走串行路径）。
 *
 * <p>期望值分两组：一组由替换前的 Bouncy Castle 参考实现生成，另一组为官方 KAT 向量
 * （RustCrypto password-hashes argon2/tests/kat.rs，对齐 phc-winner-argon2 参考实现）。
 * 两组覆盖 Argon2d/Argon2i/Argon2id 三种寻址方式。</p>
 *
 * <p>这些用例的参数都低于并行门槛（{@code mPrime < 4096}），因此固定走串行路径，
 * 作为并行化改动的基础回归；大参数的并行路径见 {@link Argon2ParallelTest}。</p>
 */
class Argon2Test {

    private static final byte[] PWD = "password123".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SALT = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    @Test
    void digestMatchesKnownVectors() {
        assertAll(
                () -> assertEquals("e7a2e6593b28c0d663e64290e0194b02841265fd476fe14347af9efabac63db6",
                        hex(Argon2.digest(PWD, SALT, 1024, 3, 4, 32)), "m=1024 t=3 p=4"),
                () -> assertEquals("cb5fa76a6cf590caf81182d1e404379944e99bfb6970969bdd9ec60afeb00fab",
                        hex(Argon2.digest(PWD, SALT, 1024, 2, 1, 32)), "m=1024 t=2 p=1"),
                () -> assertEquals("31a059aa591eef1e0b5970134a1739139f8c4671326f4cf5fdefbbeda3fb5512",
                        hex(Argon2.digest(PWD, SALT, 4096, 1, 2, 32)), "m=4096 t=1 p=2"),
                () -> assertEquals("94ee02c714e11ee78e9a7a00b1c8e1e9",
                        hex(Argon2.digest(PWD, SALT, 64, 3, 4, 16)), "m=64 t=3 p=4"));
    }

    /** 官方向量：口令 32×0x01、盐 16×0x02、secret 8×0x03、ad 12×0x04，m=32 t=3 p=4。 */
    @Test
    void officialReferenceVectors() {
        byte[] pwd = new byte[32];
        byte[] salt = new byte[16];
        byte[] secret = new byte[8];
        byte[] ad = new byte[12];
        for (int i = 0; i < pwd.length; i++) {
            pwd[i] = 0x01;
        }
        for (int i = 0; i < salt.length; i++) {
            salt[i] = 0x02;
        }
        for (int i = 0; i < secret.length; i++) {
            secret[i] = 0x03;
        }
        for (int i = 0; i < ad.length; i++) {
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

    private static String hex(byte[] out) {
        return HexFormat.of().formatHex(out);
    }
}
