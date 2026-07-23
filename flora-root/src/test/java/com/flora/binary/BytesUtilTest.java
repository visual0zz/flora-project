package com.flora.binary;

import com.flora.data.BytesUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BytesUtil 工具类的单元测试。
 * 测试 byte 与各基本类型（int/long/short/float/double）的互转、数组拼接、XOR、进制转换、反转及相等判断。
 */
class BytesUtilTest {

    // ==================== int 与 byte[] 互转 ====================

    /**
     * 测试 int 与 byte[] 之间的双向转换。
     */
    @Test
    void int2bytesAndBack() {
        int[] values = {0, 1, -1, 127, -128, 255, 256, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (int value : values) {
            byte[] bytes = BytesUtil.int2bytes(value);
            assertEquals(4, bytes.length);
            assertEquals(value, BytesUtil.bytes2int(bytes));
        }
    }

    /**
     * 验证 int 转 byte[] 的小端字节序。
     */
    @Test
    void int2bytes_le_order() {
        byte[] bytes = BytesUtil.int2bytes(0x01020304);
        assertArrayEquals(new byte[]{4, 3, 2, 1}, bytes);
    }

    /**
     * 验证 null 输入时 bytes2int 抛出异常。
     */
    @Test
    void bytes2int_null() {
        assertThrows(java.security.InvalidParameterException.class, () -> BytesUtil.bytes2int(null));
    }

    /**
     * 验证 byte[] 长度不足时 bytes2int 抛出异常。
     */
    @Test
    void bytes2int_tooShort() {
        assertThrows(java.security.InvalidParameterException.class, () -> BytesUtil.bytes2int(new byte[3]));
    }

    // ==================== long 与 byte[] 互转 ====================

    /**
     * 测试 long 与 byte[] 之间的双向转换。
     */
    @Test
    void long2bytesAndBack() {
        long[] values = {0L, 1L, -1L, 0x1234567890abcdefL, Long.MAX_VALUE, Long.MIN_VALUE};
        for (long value : values) {
            byte[] bytes = BytesUtil.long2bytes(value);
            assertEquals(8, bytes.length);
            assertEquals(value, BytesUtil.bytes2long(bytes));
        }
    }

    /**
     * 验证 long 转 byte[] 的小端字节序。
     */
    @Test
    void long2bytes_le_order() {
        byte[] bytes = BytesUtil.long2bytes(0x0102030405060708L);
        assertArrayEquals(new byte[]{8, 7, 6, 5, 4, 3, 2, 1}, bytes);
    }

    /**
     * 验证 null 输入时 bytes2long 抛出异常。
     */
    @Test
    void bytes2long_null() {
        assertThrows(java.security.InvalidParameterException.class, () -> BytesUtil.bytes2long(null));
    }

    /**
     * 验证 byte[] 长度不足时 bytes2long 抛出异常。
     */
    @Test
    void bytes2long_tooShort() {
        assertThrows(java.security.InvalidParameterException.class, () -> BytesUtil.bytes2long(new byte[7]));
    }

    // ==================== short 与 byte[] 互转 ====================

    /**
     * 测试 short 与 byte[] 之间的双向转换。
     */
    @Test
    void short2bytesAndBack() {
        short[] values = {0, 1, -1, Short.MAX_VALUE, Short.MIN_VALUE};
        for (short value : values) {
            byte[] bytes = BytesUtil.short2bytes(value);
            assertEquals(2, bytes.length);
            assertEquals(value, BytesUtil.bytes2short(bytes));
        }
    }

    /**
     * 验证 short 转 byte[] 的小端字节序。
     */
    @Test
    void short2bytes_le_order() {
        byte[] bytes = BytesUtil.short2bytes((short) 0x0102);
        assertArrayEquals(new byte[]{2, 1}, bytes);
    }

    /**
     * 验证 null 输入时 bytes2short 抛出异常。
     */
    @Test
    void bytes2short_null() {
        assertThrows(java.security.InvalidParameterException.class, () -> BytesUtil.bytes2short(null));
    }

    /**
     * 验证 byte[] 长度不足时 bytes2short 抛出异常。
     */
    @Test
    void bytes2short_tooShort() {
        assertThrows(java.security.InvalidParameterException.class, () -> BytesUtil.bytes2short(new byte[1]));
    }

    // ==================== float 与 byte[] 互转 ====================

    /**
     * 测试 float 与 byte[] 之间的双向转换，涵盖边界值和特殊值。
     */
    @Test
    void float2bytesAndBack() {
        float[] values = {0f, -0f, 1f, -1f, Float.MAX_VALUE, Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY};
        for (float value : values) {
            byte[] bytes = BytesUtil.float2bytes(value);
            assertEquals(4, bytes.length);
            assertEquals(value, BytesUtil.bytes2float(bytes), 0f);
        }
    }

    /**
     * 验证 NaN 在 float 与 byte[] 转换中的保持。
     */
    @Test
    void float2bytesNaN() {
        float original = Float.NaN;
        byte[] bytes = BytesUtil.float2bytes(original);
        assertTrue(Float.isNaN(BytesUtil.bytes2float(bytes)));
    }

    // ==================== double 与 byte[] 互转 ====================

    /**
     * 测试 double 与 byte[] 之间的双向转换，涵盖边界值和特殊值。
     */
    @Test
    void double2bytesAndBack() {
        double[] values = {0d, -0d, 1d, -1d, Double.MAX_VALUE, Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY};
        for (double value : values) {
            byte[] bytes = BytesUtil.double2bytes(value);
            assertEquals(8, bytes.length);
            assertEquals(value, BytesUtil.bytes2double(bytes), 0d);
        }
    }

    /**
     * 验证 NaN 在 double 与 byte[] 转换中的保持。
     */
    @Test
    void double2bytesNaN() {
        double original = Double.NaN;
        byte[] bytes = BytesUtil.double2bytes(original);
        assertTrue(Double.isNaN(BytesUtil.bytes2double(bytes)));
    }

    // ==================== 数组拼接 ====================

    /**
     * 测试两个非空 byte[] 的拼接。
     */
    @Test
    void concat_twoArrays() {
        byte[] a = {1, 2};
        byte[] b = {3, 4, 5};
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, BytesUtil.concat(a, b));
    }

    /**
     * 测试空数组与有数据数组的拼接。
     */
    @Test
    void concat_emptyA() {
        assertArrayEquals(new byte[]{3, 4}, BytesUtil.concat(new byte[0], new byte[]{3, 4}));
    }

    /**
     * 测试有数据数组与空数组的拼接。
     */
    @Test
    void concat_emptyB() {
        assertArrayEquals(new byte[]{1, 2}, BytesUtil.concat(new byte[]{1, 2}, new byte[0]));
    }

    /**
     * 验证第一个参数为 null 时抛出异常。
     */
    @Test
    void concat_nullA() {
        assertThrows(java.security.InvalidParameterException.class,
                () -> BytesUtil.concat(null, new byte[1]));
    }

    /**
     * 验证第二个参数为 null 时抛出异常。
     */
    @Test
    void concat_nullB() {
        assertThrows(java.security.InvalidParameterException.class,
                () -> BytesUtil.concat(new byte[1], null));
    }

    // ==================== XOR 运算 ====================

    /**
     * 测试基本 XOR 运算。
     */
    @Test
    void xor_basic() {
        byte[] a = {0x0f, (byte) 0xf0};
        byte[] b = {(byte) 0xff, 0x00};
        assertArrayEquals(new byte[]{(byte) 0xf0, (byte) 0xf0}, BytesUtil.xor(a, b));
    }

    /**
     * 测试自身 XOR 结果为全零。
     */
    @Test
    void xor_self() {
        byte[] a = {1, 2, 3};
        assertArrayEquals(new byte[3], BytesUtil.xor(a, a));
    }

    /**
     * 验证 null 输入时抛出异常。
     */
    @Test
    void xor_null() {
        assertThrows(java.security.InvalidParameterException.class,
                () -> BytesUtil.xor(null, new byte[1]));
    }

    /**
     * 验证两数组长度不匹配时抛出异常。
     */
    @Test
    void xor_lengthMismatch() {
        assertThrows(java.security.InvalidParameterException.class,
                () -> BytesUtil.xor(new byte[1], new byte[2]));
    }

    /**
     * 验证空数组输入时抛出异常。
     */
    @Test
    void xor_empty() {
        assertThrows(java.security.InvalidParameterException.class,
                () -> BytesUtil.xor(new byte[0], new byte[0]));
    }

    // ==================== 二进制字符串转换 ====================

    /**
     * 测试 byte[] 转二进制字符串。
     */
    @Test
    void bytes2binaryString() {
        byte[] bytes = {(byte) 0b10101100, (byte) 0b00001111};
        assertEquals("1010110000001111", BytesUtil.bytes2binaryString(bytes));
    }

    /**
     * 验证 null 输入时抛出异常。
     */
    @Test
    void bytes2binaryString_null() {
        assertThrows(java.security.InvalidParameterException.class,
                () -> BytesUtil.bytes2binaryString(null));
    }

    // ==================== 十六进制字符串转换 ====================

    /**
     * 测试 byte[] 转十六进制字符串。
     */
    @Test
    void bytes2hexString() {
        byte[] bytes = {(byte) 0xab, (byte) 0xcd, (byte) 0x01};
        assertEquals("abcd01", BytesUtil.bytes2hexString(bytes));
    }

    /**
     * 测试十六进制字符串转 byte[]。
     */
    @Test
    void hexString2bytes() {
        assertArrayEquals(new byte[]{(byte) 0xab, (byte) 0xcd, (byte) 0x01},
                BytesUtil.hexString2bytes("abcd01"));
    }

    /**
     * 测试十六进制转换的双向一致性。
     */
    @Test
    void hexString2bytes_roundtrip() {
        byte[] original = {0, 1, (byte) 0xff, (byte) 0x80, (byte) 0x7f, (byte) 0xab};
        String hex = BytesUtil.bytes2hexString(original);
        assertArrayEquals(original, BytesUtil.hexString2bytes(hex));
    }

    /**
     * 验证 null 输入时抛出异常。
     */
    @Test
    void hexString2bytes_null() {
        assertThrows(java.security.InvalidParameterException.class,
                () -> BytesUtil.hexString2bytes(null));
    }

    /**
     * 验证奇数长度十六进制字符串输入时抛出异常。
     */
    @Test
    void hexString2bytes_oddLength() {
        assertThrows(java.security.InvalidParameterException.class,
                () -> BytesUtil.hexString2bytes("abc"));
    }

    /**
     * 测试空字符串转换为空 byte[]。
     */
    @Test
    void hexString2bytes_empty() {
        assertArrayEquals(new byte[0], BytesUtil.hexString2bytes(""));
    }

    // ==================== 字节序反转 ====================

    /**
     * 测试偶数长度 byte[] 的顺序反转。
     */
    @Test
    void reverseOrder_evenLength() {
        byte[] input = {1, 2, 3, 4};
        assertArrayEquals(new byte[]{4, 3, 2, 1}, BytesUtil.reverseOrder(input));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, input);
    }

    /**
     * 测试奇数长度 byte[] 的顺序反转。
     */
    @Test
    void reverseOrder_oddLength() {
        byte[] input = {1, 2, 3, 4, 5};
        assertArrayEquals(new byte[]{5, 4, 3, 2, 1}, BytesUtil.reverseOrder(input));
    }

    /**
     * 测试单元素 byte[] 的顺序反转。
     */
    @Test
    void reverseOrder_singleElement() {
        assertArrayEquals(new byte[]{42}, BytesUtil.reverseOrder(new byte[]{42}));
    }

    /**
     * 测试空 byte[] 的顺序反转。
     */
    @Test
    void reverseOrder_empty() {
        assertArrayEquals(new byte[0], BytesUtil.reverseOrder(new byte[0]));
    }

    /**
     * 验证 null 输入时抛出异常。
     */
    @Test
    void reverseOrder_null() {
        assertThrows(java.security.InvalidParameterException.class,
                () -> BytesUtil.reverseOrder(null));
    }

    /**
     * 验证 reverseOrder 不修改原数组。
     */
    @Test
    void reverseOrder_notModifyInput() {
        byte[] input = {1, 2, 3};
        byte[] result = BytesUtil.reverseOrder(input);
        assertArrayEquals(new byte[]{3, 2, 1}, result);
        assertArrayEquals(new byte[]{1, 2, 3}, input);
    }

    // ==================== 比特位反转 ====================

    /**
     * 测试 byte[] 的比特位反转。
     */
    @Test
    void reverseBits_allBits() {
        byte[] input = {(byte) 0b10110010};
        assertArrayEquals(new byte[]{(byte) 0x4d}, BytesUtil.reverseBits(input));
    }

    /**
     * 测试全零 byte[] 的比特位反转。
     */
    @Test
    void reverseBits_zero() {
        assertArrayEquals(new byte[]{0}, BytesUtil.reverseBits(new byte[]{0}));
    }

    /**
     * 测试全一 byte[] (-1) 的比特位反转结果不变。
     */
    @Test
    void reverseBits_minusOne() {
        assertArrayEquals(new byte[]{(byte) -1}, BytesUtil.reverseBits(new byte[]{(byte) -1}));
    }

    /**
     * 测试 0x0f 与 0xf0 之间的比特位反转。
     */
    @Test
    void reverseBits_0f_f0() {
        assertArrayEquals(new byte[]{(byte) 0xf0}, BytesUtil.reverseBits(new byte[]{0x0f}));
    }

    /**
     * 测试比特位反转两次后恢复原值。
     */
    @Test
    void reverseBits_roundtrip() {
        byte[] input = {(byte) 0xa1, (byte) 0xb2, (byte) 0xc3};
        byte[] once = BytesUtil.reverseBits(input);
        byte[] twice = BytesUtil.reverseBits(once);
        assertArrayEquals(input, twice);
    }

    /**
     * 测试多字节的比特位反转。
     */
    @Test
    void reverseBits_multiBytes() {
        byte[] input = {(byte) 0b11000000, (byte) 0b00110011};
        assertArrayEquals(new byte[]{0x03, (byte) 0xcc}, BytesUtil.reverseBits(input));
    }

    /**
     * 测试空 byte[] 的比特位反转。
     */
    @Test
    void reverseBits_empty() {
        assertArrayEquals(new byte[0], BytesUtil.reverseBits(new byte[0]));
    }

    /**
     * 验证 null 输入时抛出异常。
     */
    @Test
    void reverseBits_null() {
        assertThrows(java.security.InvalidParameterException.class,
                () -> BytesUtil.reverseBits(null));
    }

    // ==================== 相等判断 ====================

    /**
     * 测试相等 byte[] 的相等判断。
     */
    @Test
    void isEqual_equal() {
        assertTrue(BytesUtil.isEqual(new byte[]{1, 2, 3}, new byte[]{1, 2, 3}));
    }

    /**
     * 测试不相等 byte[] 的相等判断。
     */
    @Test
    void isEqual_notEqual() {
        assertFalse(BytesUtil.isEqual(new byte[]{1, 2, 3}, new byte[]{1, 2, 4}));
    }

    /**
     * 测试不同长度 byte[] 的相等判断。
     */
    @Test
    void isEqual_diffLength() {
        assertFalse(BytesUtil.isEqual(new byte[]{1, 2}, new byte[]{1, 2, 3}));
    }

    /**
     * 验证 null 输入时抛出异常。
     */
    @Test
    void isEqual_null() {
        assertThrows(java.security.InvalidParameterException.class,
                () -> BytesUtil.isEqual(null, new byte[1]));
    }
}
