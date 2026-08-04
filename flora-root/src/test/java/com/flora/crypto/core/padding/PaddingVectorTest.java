package com.flora.crypto.core.padding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对称填充方案的直接单元测试。
 * <p>ISO 7816-4 和 ZeroByte 填充在 JDK 中没有对应实现，无法做交叉验证，
 * 通过直接验证 addPadding / padCount 的字节模式与 round-trip 正确性来保证。</p>
 */
class PaddingVectorTest {

    // ── PKCS7：addPadding 字节模式 ──

    @Test
    void pkcs7AddPaddingPattern() {
        // 块大小 16，数据占 10 字节 → 需填充 6 字节，每字节值 0x06
        byte[] block = new byte[16];
        for (int i = 0; i < 10; i++) block[i] = (byte) i;
        PKCS7Padding padding = new PKCS7Padding();
        int added = padding.addPadding(block, 10);

        assertEquals(6, added);
        for (int i = 10; i < 16; i++) {
            assertEquals(6, block[i] & 0xFF, "PKCS7 填充字节值应等于填充长度");
        }
    }

    @Test
    void pkcs7FullBlockPadding() {
        // 数据恰好块对齐 → 仍填充整块（16 字节 0x10）
        byte[] block = new byte[16];
        PKCS7Padding padding = new PKCS7Padding();
        int added = padding.addPadding(block, 0);

        assertEquals(16, added);
        for (byte b : block) {
            assertEquals(16, b & 0xFF);
        }
    }

    @Test
    void pkcs7PadCount() {
        byte[] block = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 6, 6, 6, 6, 6, 6};
        PKCS7Padding padding = new PKCS7Padding();
        assertEquals(6, padding.padCount(block));
    }

    @Test
    void pkcs7InvalidPaddingThrows() {
        PKCS7Padding padding = new PKCS7Padding();
        // 填充值 0（非法）
        byte[] bad = new byte[16];
        assertThrows(IllegalStateException.class, () -> padding.padCount(bad));
        // 填充值超过块大小
        byte[] bad2 = new byte[16];
        bad2[15] = 17;
        assertThrows(IllegalStateException.class, () -> padding.padCount(bad2));
    }

    @Test
    void pkcs7RoundTrip() {
        PKCS7Padding padding = new PKCS7Padding();
        for (int dataLen = 0; dataLen < 16; dataLen++) {
            byte[] block = new byte[16];
            for (int i = 0; i < dataLen; i++) block[i] = (byte) (i + 1);
            padding.addPadding(block, dataLen);
            int padCount = padding.padCount(block);
            assertEquals(16 - dataLen, padCount, "dataLen=" + dataLen);
        }
    }

    // ── ISO 7816-4：addPadding 字节模式 ──

    @Test
    void iso7816AddPaddingPattern() {
        // 块大小 16，数据占 10 字节 → 0x80 + 5 个 0x00
        byte[] block = new byte[16];
        for (int i = 0; i < 10; i++) block[i] = (byte) i;
        ISO7816d4Padding padding = new ISO7816d4Padding();
        int added = padding.addPadding(block, 10);

        assertEquals(6, added);
        assertEquals((byte) 0x80, block[10], "首个填充字节应为 0x80");
        for (int i = 11; i < 16; i++) {
            assertEquals(0, block[i], "后续填充字节应为 0x00");
        }
    }

    @Test
    void iso7816FullBlockPadding() {
        // 数据恰好块对齐 → 填充整块（0x80 + 15 个 0x00）
        byte[] block = new byte[16];
        ISO7816d4Padding padding = new ISO7816d4Padding();
        int added = padding.addPadding(block, 0);

        assertEquals(16, added);
        assertEquals((byte) 0x80, block[0]);
        for (int i = 1; i < 16; i++) {
            assertEquals(0, block[i]);
        }
    }

    @Test
    void iso7816PadCount() {
        byte[] block = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, (byte) 0x80, 0, 0, 0, 0, 0};
        ISO7816d4Padding padding = new ISO7816d4Padding();
        assertEquals(6, padding.padCount(block));
    }

    @Test
    void iso7816InvalidPaddingThrows() {
        ISO7816d4Padding padding = new ISO7816d4Padding();
        // 没有 0x80 标记
        byte[] bad = new byte[16];
        assertThrows(IllegalStateException.class, () -> padding.padCount(bad));
    }

    @Test
    void iso7816RoundTrip() {
        ISO7816d4Padding padding = new ISO7816d4Padding();
        for (int dataLen = 0; dataLen < 16; dataLen++) {
            byte[] block = new byte[16];
            for (int i = 0; i < dataLen; i++) block[i] = (byte) (i + 1);
            padding.addPadding(block, dataLen);
            int padCount = padding.padCount(block);
            assertEquals(16 - dataLen, padCount, "dataLen=" + dataLen);
        }
    }

    // ── ZeroByte：addPadding 字节模式 ──

    @Test
    void zeroByteAddPaddingPattern() {
        byte[] block = new byte[16];
        for (int i = 0; i < 10; i++) block[i] = (byte) (i + 1);
        ZeroBytePadding padding = new ZeroBytePadding();
        int added = padding.addPadding(block, 10);

        assertEquals(6, added);
        for (int i = 10; i < 16; i++) {
            assertEquals(0, block[i], "填充字节应全为 0x00");
        }
    }

    @Test
    void zeroBytePadCount() {
        byte[] block = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0, 0, 0, 0, 0, 0};
        ZeroBytePadding padding = new ZeroBytePadding();
        assertEquals(6, padding.padCount(block));
    }

    @Test
    void zeroByteNoTrailingZeros() {
        // 最后一个字节非 0 → padCount = 0
        byte[] block = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        ZeroBytePadding padding = new ZeroBytePadding();
        assertEquals(0, padding.padCount(block));
    }

    @Test
    void zeroByteRoundTrip() {
        ZeroBytePadding padding = new ZeroBytePadding();
        // 注意：ZeroByte 的 round-trip 仅在明文末尾非 0x00 时可逆
        for (int dataLen = 1; dataLen < 16; dataLen++) {
            byte[] block = new byte[16];
            for (int i = 0; i < dataLen; i++) block[i] = (byte) (i + 1); // 确保末尾非 0
            padding.addPadding(block, dataLen);
            int padCount = padding.padCount(block);
            assertEquals(16 - dataLen, padCount, "dataLen=" + dataLen);
        }
    }
}
