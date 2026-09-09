package com.flora.sanctum.core.crypto;

import com.flora.sanctum.core.crypto.impl.GcmSiv;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自研 GCM-SIV 与 Bouncy Castle 参考实现的 KAT 回归 + RFC 官方向量 + 往返/篡改测试。
 * <p>基于 AES-256、12 字节 nonce 的 GCM-SIV 已知答案向量（KAT），用于回归验证
 * 加解密输出与已知向量逐字节一致。KAT 字符串是测试向量而非真实密钥，故抑制 secret 检查。</p>
 */
@SuppressWarnings("osmetes:secret")
class GcmSivTest {

    private static final byte[] KEY = new byte[32];
    private static final byte[] NONCE = {3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14};

    static {
        for (int i = 0; i < 32; i++) {
            KEY[i] = (byte) (0x10 + i);
        }
    }

    @Test
    void encryptMatchesBcReference() {
        assertEncrypt("dba9abcef52b59fa56b5c2df9b0cf92a", new byte[0], new byte[0]);
        assertEncrypt("032f1a1f85da0820fdd5140d596ab772a0c37bb1456bc1a4", new byte[0],
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        assertEncrypt("ffe121e725f49e466209d3b8f98ea66a634d86c4f1c2f0ff6626c8d2d98966b7",
                "aad-data-12345".getBytes(StandardCharsets.UTF_8),
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        assertEncrypt("2723ce856b7ed571b74d4e3ce4eb8d964da6100c301506be1a2de691ef505179"
                        + "1343c3aa3a0b0e6139b3192fc0347fbbefcff15d7c2e9dc8418467d8f1cb2598eadd37dc0fee",
                "associated-data".getBytes(StandardCharsets.UTF_8),
                "the quick brown fox jumps over the lazy dog 0123456789".getBytes(StandardCharsets.UTF_8));
        assertEncrypt("43c278e3b9acd1bca13c854aadb226228c826debec1d6afa3e012891236b90c74f9540cc034e67",
                ("aad-" + "x".repeat(300)).getBytes(StandardCharsets.UTF_8),
                "payload-payload-payload".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rfc8452Vectors() {
        // RFC 8452 AEAD_AES_256_GCM_SIV 官方向量 1/2
        byte[] key = HexFormat.of().parseHex("0100000000000000000000000000000000000000000000000000000000000000");
        byte[] nonce = HexFormat.of().parseHex("030000000000000000000000");
        // 向量 1：空明文空 AAD
        assertEquals("07f5f4169bbf55a8400cd47ea6fd400f",
                HexFormat.of().formatHex(GcmSiv.encrypt(key, nonce, new byte[0], new byte[0])));
        // 向量 2：8 字节明文无 AAD
        assertEquals("c2ef328e5c71c83b843122130f7364b761e0b97427e3df28",
                HexFormat.of().formatHex(GcmSiv.encrypt(key, nonce, new byte[0],
                        HexFormat.of().parseHex("0100000000000000"))));
    }

    @Test
    void roundTrip() {
        byte[] aad = "header-aad".getBytes(StandardCharsets.UTF_8);
        byte[] pt = "sensitive secret payload 123".getBytes(StandardCharsets.UTF_8);
        byte[] out = GcmSiv.encrypt(KEY, NONCE, aad, pt);
        byte[] back = GcmSiv.decrypt(KEY, NONCE, aad, out);
        assertArrayEquals(pt, back);
    }

    @Test
    void emptyRoundTrip() {
        byte[] out = GcmSiv.encrypt(KEY, NONCE, new byte[0], new byte[0]);
        assertEquals(16, out.length);
        assertArrayEquals(new byte[0], GcmSiv.decrypt(KEY, NONCE, new byte[0], out));
    }

    @Test
    void tamperedTagRejected() {
        byte[] out = GcmSiv.encrypt(KEY, NONCE, new byte[0], new byte[]{1, 2, 3, 4});
        out[out.length - 1] ^= 0x01;
        assertThrows(IllegalArgumentException.class,
                () -> GcmSiv.decrypt(KEY, NONCE, new byte[0], out));
    }

    @Test
    void tamperedCiphertextRejected() {
        byte[] out = GcmSiv.encrypt(KEY, NONCE, "aad".getBytes(StandardCharsets.UTF_8),
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        out[0] ^= 0x01;
        assertThrows(IllegalArgumentException.class,
                () -> GcmSiv.decrypt(KEY, NONCE, "aad".getBytes(StandardCharsets.UTF_8), out));
    }

    @Test
    void wrongAadRejected() {
        byte[] out = GcmSiv.encrypt(KEY, NONCE, "aad1".getBytes(StandardCharsets.UTF_8), new byte[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class,
                () -> GcmSiv.decrypt(KEY, NONCE, "aad2".getBytes(StandardCharsets.UTF_8), out));
    }

    private static void assertEncrypt(String expected, byte[] aad, byte[] pt) {
        byte[] out = GcmSiv.encrypt(KEY, NONCE, aad, pt);
        assertEquals(expected, HexFormat.of().formatHex(out));
    }
}
