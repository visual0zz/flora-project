package com.flora.data;

import java.security.InvalidParameterException;


/**
 * 字节数组工具类，提供字节数组与基本数据类型（short/int/long/float/double）之间的
 * 相互转换，以及十六进制字符串、二进制字符串、异或、拼接等操作。
 */
public final class BytesUtil {

    /** 十六进制字符查找表 */
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    

    /**
     * 将 short 转换为 2 字节小端序字节数组。
     *
     * @param value short 值
     * @return 2 字节数组（小端序）
     */
    public static byte[] short2bytes(short value) {
        byte[] targets = new byte[2];
        targets[0] = (byte) (value & 0xff);
        targets[1] = (byte) ((value >> 8) & 0xff);
        return targets;
    }

    /**
     * 将 2 字节小端序字节数组转换为 short。
     *
     * @param bytes 字节数组，长度至少 2
     * @return short 值
     * @throws InvalidParameterException 如果参数为空或长度不足
     */
    public static short bytes2short(byte[] bytes) {
        if (bytes == null) throw new InvalidParameterException("参数不能为空。");
        if (bytes.length < 2) throw new InvalidParameterException("数组长度至少为2字节。");
        return (short) ((bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8));
    }

    /**
     * 将 int 转换为 4 字节小端序字节数组。
     *
     * @param value int 值
     * @return 4 字节数组（小端序）
     */
    public static byte[] int2bytes(int value) {
        byte[] targets = new byte[4];
        targets[0] = (byte) (value & 0xff);       
        targets[1] = (byte) ((value >> 8) & 0xff); 
        targets[2] = (byte) ((value >> 16) & 0xff);
        targets[3] = (byte) (value >>> 24);        
        return targets;
    }

    /**
     * 将 4 字节小端序字节数组转换为 int。
     *
     * @param bytes 字节数组，长度至少 4
     * @return int 值
     * @throws InvalidParameterException 如果参数为空或长度不足
     */
    public static int bytes2int(byte[] bytes) {
        if (bytes == null) throw new InvalidParameterException("参数不能为空。");
        if (bytes.length < 4) {
            throw new InvalidParameterException("数组长度至少为4字节。");
        }
        return (bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8)
                | ((bytes[2] & 0xff) << 16) | ((bytes[3] & 0xff) << 24);
    }

    /**
     * 将 long 转换为 8 字节小端序字节数组。
     *
     * @param value long 值
     * @return 8 字节数组（小端序）
     */
    public static byte[] long2bytes(long value) {
        byte[] targets = new byte[8];
        targets[0] = (byte) (value & 0xff);
        targets[1] = (byte) ((value >> 8) & 0xff);
        targets[2] = (byte) ((value >> 16) & 0xff);
        targets[3] = (byte) ((value >> 24) & 0xff);
        targets[4] = (byte) ((value >> 32) & 0xff);
        targets[5] = (byte) ((value >> 40) & 0xff);
        targets[6] = (byte) ((value >> 48) & 0xff);
        targets[7] = (byte) ((value >> 56) & 0xff);
        return targets;

    }

    /**
     * 将 8 字节小端序字节数组转换为 long。
     *
     * @param bytes 字节数组，长度至少 8
     * @return long 值
     * @throws InvalidParameterException 如果参数为空或长度不足
     */
    public static long bytes2long(byte[] bytes) {
        if (bytes == null) throw new InvalidParameterException("参数不能为空。");
        if (bytes.length < 8) {
            throw new InvalidParameterException("数组长度至少为8字节。");
        }
        return (bytes[0] & 0xffL)
                | (((long) bytes[1] << 8) & 0xff00L)
                | (((long) bytes[2] << 16) & 0xff0000L)
                | (((long) bytes[3] << 24) & 0xff000000L)
                | (((long) bytes[4] << 32) & 0xff00000000L)
                | (((long) bytes[5] << 40) & 0xff0000000000L)
                | (((long) bytes[6] << 48) & 0xff000000000000L)
                | (((long) bytes[7] << 56) & 0xff00000000000000L);
    }

    /**
     * 将 float 转换为 4 字节小端序字节数组（利用 IEEE 754 位模式）。
     *
     * @param value float 值
     * @return 4 字节数组（小端序）
     */
    public static byte[] float2bytes(float value) {
        return int2bytes(Float.floatToRawIntBits(value));
    }

    /**
     * 将 4 字节小端序字节数组转换为 float。
     *
     * @param bytes 字节数组，长度至少 4
     * @return float 值
     */
    public static float bytes2float(byte[] bytes) {
        return Float.intBitsToFloat(bytes2int(bytes));
    }

    /**
     * 将 double 转换为 8 字节小端序字节数组（利用 IEEE 754 位模式）。
     *
     * @param value double 值
     * @return 8 字节数组（小端序）
     */
    public static byte[] double2bytes(double value) {
        return long2bytes(Double.doubleToRawLongBits(value));
    }

    /**
     * 将 8 字节小端序字节数组转换为 double。
     *
     * @param bytes 字节数组，长度至少 8
     * @return double 值
     */
    public static double bytes2double(byte[] bytes) {
        return Double.longBitsToDouble(bytes2long(bytes));
    }

    

    /**
     * 将字节数组转换为十六进制字符串。
     *
     * @param bytes 字节数组，不能为空
     * @return 十六进制字符串（小写）
     * @throws InvalidParameterException 如果参数为空
     */
    public static String bytes2hexString(byte[] bytes) {
        if (bytes == null) throw new InvalidParameterException("参数不能为空。");
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0f];
        }
        return new String(hexChars);
    }

    /**
     * 将十六进制字符串转换为字节数组。
     *
     * @param hex 十六进制字符串，不能为空，长度必须为偶数
     * @return 字节数组
     * @throws InvalidParameterException 如果参数为空、长度不是偶数或包含非法字符
     */
    public static byte[] hexString2bytes(String hex) {
        if (hex == null) throw new InvalidParameterException("参数不能为空。");
        int len = hex.length();
        if (len % 2 != 0) throw new InvalidParameterException("十六进制字符串长度必须为偶数。");
        byte[] result = new byte[len / 2];
        for (int i = 0; i < result.length; i++) {
            int index = i * 2;
            int high = Character.digit(hex.charAt(index), 16);
            int low = Character.digit(hex.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                throw new InvalidParameterException("十六进制字符串包含非法字符。");
            }
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    /**
     * 将字节数组转换为二进制字符串（每位一个字符 '0' 或 '1'）。
     *
     * @param bytes 字节数组，不能为空
     * @return 二进制字符串
     * @throws InvalidParameterException 如果参数为空
     */
    public static String bytes2binaryString(byte[] bytes) {
        if (bytes == null) throw new InvalidParameterException("参数不能为空。");
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append((b >> 7) & 0x01);
            builder.append((b >> 6) & 0x01);
            builder.append((b >> 5) & 0x01);
            builder.append((b >> 4) & 0x01);
            builder.append((b >> 3) & 0x01);
            builder.append((b >> 2) & 0x01);
            builder.append((b >> 1) & 0x01);
            builder.append(b & 0x01);
        }
        return builder.toString();
    }

    

    /**
     * 拼接两个字节数组。
     *
     * @param a 第一个字节数组，不能为空
     * @param b 第二个字节数组，不能为空
     * @return 拼接后的字节数组
     * @throws InvalidParameterException 如果参数为空或合并后长度超出限制
     */
    public static byte[] concat(byte[] a, byte[] b) {
        if (a == null || b == null) throw new InvalidParameterException("参数不能为空。");

        int alen = a.length;
        int blen = b.length;
        if ((long) alen + blen > Integer.MAX_VALUE) {
            throw new InvalidParameterException("合并后数组长度超出限制。");
        }
        if (alen == 0) {
            return b;
        }
        if (blen == 0) {
            return a;
        }
        byte[] result = new byte[alen + blen];
        System.arraycopy(a, 0, result, 0, alen);
        System.arraycopy(b, 0, result, alen, blen);
        return result;
    }

    /**
     * 反转字节数组的顺序。
     *
     * @param bytes 字节数组，不能为空
     * @return 顺序反转后的新字节数组
     * @throws InvalidParameterException 如果参数为空
     */
    public static byte[] reverseOrder(byte[] bytes) {
        if (bytes == null) throw new InvalidParameterException("参数不能为空。");
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = bytes[bytes.length - 1 - i];
        }
        return result;
    }

    /**
     * 反转字节数组每个字节的比特顺序（按位反转）。
     *
     * @param bytes 字节数组，不能为空
     * @return 每个字节按位反转后的新字节数组
     * @throws InvalidParameterException 如果参数为空
     */
    public static byte[] reverseBits(byte[] bytes) {
        if (bytes == null) throw new InvalidParameterException("参数不能为空。");
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xff;
            int reversed = 0;
            for (int j = 0; j < 8; j++) {
                reversed = (reversed << 1) | (b & 1);
                b >>= 1;
            }
            result[i] = (byte) reversed;
        }
        return result;
    }

    /**
     * 对两个等长字节数组进行按位异或运算。
     *
     * @param a 第一个字节数组，不能为空
     * @param b 第二个字节数组，不能为空，长度必须与 a 相同
     * @return 异或结果字节数组
     * @throws InvalidParameterException 如果参数为空、长度不相等或长度为 0
     */
    public static byte[] xor(byte[] a, byte[] b) {
        if (a == null || b == null) throw new InvalidParameterException("参数不能为空。");
        if (a.length != b.length) throw new InvalidParameterException("参与计算必须是两个等长数组。");
        if (a.length == 0) throw new InvalidParameterException("数组长度必须大于0。");

        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    /**
     * 比较两个字节数组是否相等（长度相同且每个字节相同）。
     *
     * @param a 第一个字节数组，不能为空
     * @param b 第二个字节数组，不能为空
     * @return 如果相等返回 true，否则返回 false
     * @throws InvalidParameterException 如果参数为空
     */
    public static boolean isEqual(byte[] a, byte[] b) {
        if (a == null || b == null) throw new InvalidParameterException("参数不能为空。");
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }
}
