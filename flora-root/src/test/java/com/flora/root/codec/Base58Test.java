package com.flora.root.codec;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Base58Test {

    @Test
    void encodeEmptyReturnsEmpty() {
        assertEquals("", Base58.encode(new byte[0]));
    }

    @Test
    void decodeEmptyReturnsEmptyArray() {
        assertArrayEquals(new byte[0], Base58.decode(""));
    }

    @Test
    void knownVectorHelloWorld() {
        // 知名测试向量："Hello World" 的 Base58 编码
        assertEquals("JxF12TrwUP45BMd", Base58.encode("Hello World"));
        assertEquals("Hello World", Base58.decodeToString("JxF12TrwUP45BMd"));
    }

    @Test
    @SuppressWarnings("osmetes:secret") // 测试向量形似高熵密钥，非真实密钥
    void knownVectorFox() {
        // 长文本的 Base58 编码（期望值经 Python 独立实现交叉验证）
        String expected = "USm3fpXnKG5EUBx2ndxBDMPVciP5hGey2Jh4NDv6gmeo1LkMeiKrLJUUBk6Z";
        String s = "The quick brown fox jumps over the lazy dog.";
        assertEquals(expected, Base58.encode(s));
        assertEquals(s, Base58.decodeToString(expected));
    }

    @Test
    void singleByteValueMapping() {
        // 字节值 0..57 直接映射到字母表索引
        assertEquals("1", Base58.encode(new byte[]{0}));
        assertEquals("2", Base58.encode(new byte[]{1}));
        assertEquals("z", Base58.encode(new byte[]{57}));
    }

    @Test
    void leadingZeroBytesMapToOnes() {
        assertEquals("111", Base58.encode(new byte[]{0, 0, 0}));
        assertEquals("1z", Base58.encode(new byte[]{0, 57}));
        assertArrayEquals(new byte[]{0, 0, 0}, Base58.decode("111"));
        assertArrayEquals(new byte[]{0, 57}, Base58.decode("1z"));
    }

    @Test
    void roundTripRandomData() {
        Random random = new Random(42);
        for (int len : new int[]{1, 2, 7, 16, 64, 256}) {
            byte[] data = new byte[len];
            random.nextBytes(data);
            assertArrayEquals(data, Base58.decode(Base58.encode(data)), "长度 " + len);
        }
    }

    @Test
    void roundTripWithLeadingZeros() {
        byte[] data = {0, 0, (byte) 0x7F, (byte) 0x80, (byte) 0xFF};
        String b58 = Base58.encode(data);
        assertTrue(b58.startsWith("1"));
        assertArrayEquals(data, Base58.decode(b58));
    }

    @Test
    void utf8RoundTrip() {
        String str = "中文字符与 emoji 🎉";
        assertEquals(str, Base58.decodeToString(Base58.encode(str)));
    }

    @Test
    void isValidBase58AcceptsAlphabet() {
        assertTrue(Base58.isValidBase58("JxF12TrwUP45BMd"));
        assertTrue(Base58.isValidBase58("1"));
    }

    @Test
    void isValidBase58RejectsConfusingAndInvalidChars() {
        // 剔除的易混淆字符
        assertFalse(Base58.isValidBase58("0"));
        assertFalse(Base58.isValidBase58("O"));
        assertFalse(Base58.isValidBase58("I"));
        assertFalse(Base58.isValidBase58("l"));
        // 非法字符与空
        assertFalse(Base58.isValidBase58("a+b"));
        assertFalse(Base58.isValidBase58("a/b"));
        assertFalse(Base58.isValidBase58(""));
        assertFalse(Base58.isValidBase58(null));
    }

    @Test
    @SuppressWarnings("osmetes:secret") // 测试向量形似高熵密钥，非真实密钥
    void decodeInvalidCharsThrows() {
        assertThrows(IllegalArgumentException.class, () -> Base58.decode("0OIl"));
        assertThrows(IllegalArgumentException.class, () -> Base58.decode("JxF12TrwUP45BMd+"));
    }

    @Test
    void encodeNullThrows() {
        // CheckUtil.notNull 对 null 抛 NullPointerException
        assertThrows(NullPointerException.class, () -> Base58.encode((byte[]) null));
        assertThrows(NullPointerException.class, () -> Base58.encode((String) null));
        assertThrows(NullPointerException.class, () -> Base58.decode(null));
    }

    @Test
    void allByteValuesRoundTrip() {
        // 每个字节值 0-255 都能无损往返
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
        }
        assertArrayEquals(all, Base58.decode(Base58.encode(all)));
    }

    @Test
    void noPaddingCharProduced() {
        // Base58 无填充符，任何编码结果都不应含 '='
        byte[] data = "Base58 has no padding".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(Base58.encode(data).contains("="));
    }
}
