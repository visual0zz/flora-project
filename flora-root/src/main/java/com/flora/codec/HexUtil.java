package com.flora.codec;

import com.flora.java.CheckUtil;

import java.nio.charset.StandardCharsets;

/**
 * 十六进制编解码工具类。
 * <p>提供字节数组与十六进制字符串之间的双向转换，同时支持字符串（UTF-8 编码）的编码解码，
 * 以及十六进制格式的合法性校验。</p>
 */
public final class HexUtil {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static final byte[] HEX_TO_BYTE = new byte[128];

    static {
        for (int i = 0; i < HEX_DIGITS.length; i++) {
            HEX_TO_BYTE[HEX_DIGITS[i]] = (byte) i;
        }
        
        for (int i = 10; i < 16; i++) {
            HEX_TO_BYTE['A' + i - 10] = (byte) i;
        }
    }

    private HexUtil() {
    }

    /**
     * 将字节数组编码为十六进制字符串。
     *
     * @param data 要编码的字节数组，不能为 null
     * @return 小写十六进制字符串
     */
    public static String encodeHex(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        char[] hexChars = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xFF;
            hexChars[i * 2] = HEX_DIGITS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * 将字符串（UTF-8 编码）编码为十六进制字符串。
     *
     * @param str 要编码的字符串，不能为 null
     * @return 小写十六进制字符串
     */
    public static String encodeHex(String str) {
        CheckUtil.notNull(str, "字符串不能为空");
        return encodeHex(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将十六进制字符串解码为字节数组。
     *
     * @param hex 十六进制字符串（大小写均可），长度必须为偶数，不能为 null
     * @return 解码后的字节数组
     */
    public static byte[] decodeHex(String hex) {
        CheckUtil.notNull(hex, "十六进制字符串不能为空");
        int len = hex.length();
        CheckUtil.mustTrue(len % 2 == 0, "十六进制字符串长度必须为偶数");
        byte[] data = new byte[len / 2];
        for (int i = 0; i < data.length; i++) {
            int high = HEX_TO_BYTE[hex.charAt(i * 2)] & 0xFF;
            int low = HEX_TO_BYTE[hex.charAt(i * 2 + 1)] & 0xFF;
            data[i] = (byte) ((high << 4) | low);
        }
        return data;
    }

    /**
     * 将十六进制字符串解码为 UTF-8 字符串。
     *
     * @param hex 十六进制字符串，不能为 null
     * @return 解码后的 UTF-8 字符串
     */
    public static String decodeHexToString(String hex) {
        return new String(decodeHex(hex), StandardCharsets.UTF_8);
    }

    /**
     * 检查字符串是否为有效的十六进制表示。
     * <p>有效的十六进制字符串必须满足：非 null、非空、长度为偶数、仅包含 0-9a-fA-F 字符。</p>
     *
     * @param str 待检查的字符串
     * @return 如果是有效的十六进制字符串则返回 true
     */
    public static boolean isValidHex(String str) {
        if (str == null || str.isEmpty() || str.length() % 2 != 0) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }
}
