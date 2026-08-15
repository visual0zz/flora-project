package com.flora.sanctum.store;

/**
 * Base58 编解码（排除易混淆的 0/O/l/I）。
 * <p>
 * 字母表：123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz。
 * 块以 base58 字符串落在 markdown 文件中（见设计 04b）。
 */
public final class Base58 {

    private static final String ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final char[] ALPHABET_CHARS = ALPHABET.toCharArray();
    private static final int[] INDEX = new int[128];

    static {
        java.util.Arrays.fill(INDEX, -1);
        for (int i = 0; i < ALPHABET_CHARS.length; i++) {
            INDEX[ALPHABET_CHARS[i]] = i;
        }
    }

    private Base58() {
    }

    /** 编码字节为 base58 字符串。 */
    public static String encode(byte[] input) {
        if (input.length == 0) {
            return "";
        }
        // 数零字节前缀
        int zeros = 0;
        while (zeros < input.length && input[zeros] == 0) {
            zeros++;
        }
        // 基数转换：256 进制 → 58 进制
        byte[] encoded = new byte[input.length * 138 / 100 + 1];
        int length = 0;
        for (int i = zeros; i < input.length; i++) {
            int carry = input[i] & 0xFF;
            for (int j = 0; j < encoded.length; j++) {
                carry += (encoded[j] & 0xFF) * 256;
                encoded[j] = (byte) (carry % 58);
                carry /= 58;
            }
            length = encoded.length;
        }
        // 计算实际长度（跳过尾部零）
        int start = encoded.length - 1;
        while (start >= 0 && encoded[start] == 0) {
            start--;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < zeros; i++) {
            sb.append(ALPHABET_CHARS[0]);
        }
        for (int i = start; i >= 0; i--) {
            sb.append(ALPHABET_CHARS[encoded[i] & 0xFF]);
        }
        return sb.toString();
    }

    /** 解码 base58 字符串为字节。 */
    public static byte[] decode(String input) {
        if (input.isEmpty()) {
            return new byte[0];
        }
        int zeros = 0;
        while (zeros < input.length() && input.charAt(zeros) == ALPHABET_CHARS[0]) {
            zeros++;
        }
        byte[] decoded = new byte[input.length() * 733 / 1000 + 1];
        int length = 0;
        for (int i = zeros; i < input.length(); i++) {
            char c = input.charAt(i);
            int val;
            if (c >= 128 || (val = INDEX[c]) < 0) {
                throw new IllegalArgumentException("invalid base58 character: " + c);
            }
            int carry = val;
            for (int j = 0; j < decoded.length; j++) {
                carry += (decoded[j] & 0xFF) * 58;
                decoded[j] = (byte) (carry % 256);
                carry /= 256;
            }
            length = decoded.length;
        }
        int start = decoded.length - 1;
        while (start >= 0 && decoded[start] == 0) {
            start--;
        }
        byte[] result = new byte[zeros + (start + 1)];
        int pos = 0;
        for (int i = 0; i < zeros; i++) {
            result[pos++] = 0;
        }
        for (int i = start; i >= 0; i--) {
            result[pos++] = decoded[i];
        }
        return result;
    }

    /** 判断一串字符是否全部是合法 base58 字符（长度&gt;0）。 */
    public static boolean isBase58(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 128 || INDEX[c] < 0) {
                return false;
            }
        }
        return true;
    }
}
