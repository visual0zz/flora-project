package com.flora.root.codec;

/**
 * Base32 编解码（RFC 4648 无填充，忽略空白/大小写，用于 TOTP 种子等）。
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] INDEX = new int[128];

    static {
        java.util.Arrays.fill(INDEX, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            INDEX[ALPHABET.charAt(i)] = i;
        }
    }

    private Base32() {
    }

    /** 编码字节为 base32 字符串（无填充）。 */
    public static String encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    /** 解码 base32 字符串（忽略空白与 '=' 填充，不区分大小写）。 */
    public static byte[] decode(String b32) {
        String s = b32.replaceAll("[\\s=]", "").toUpperCase();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 128 || INDEX[c] < 0) {
                throw new IllegalArgumentException("invalid base32 char: " + c);
            }
            buffer = (buffer << 5) | INDEX[c];
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    /** 判断字符串是否为合法 base32（忽略空白与填充）。 */
    public static boolean isValidBase32(String s) {
        String t = s.replaceAll("[\\s=]", "").toUpperCase();
        if (t.isEmpty()) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 128 || INDEX[c] < 0) {
                return false;
            }
        }
        return true;
    }
}
