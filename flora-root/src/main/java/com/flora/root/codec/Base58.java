package com.flora.root.codec;

import com.flora.root.java.CheckUtil;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Base58 编解码工具类。
 * <p>字符集为 {@code 1-9A-HJ-NP-Za-km-z}（比特币标准），在 Base62 的基础上剔除易混淆的
 * {@code 0/O/I/l}，便于人工抄写与口头传递。纯字母数字，无填充符，适合短 ID、地址等场景。</p>
 * <p>编码规则：把输入视为大端序大整数，除以 58 取余映射字符；开头的 {@code 0x00} 字节
 * 各映射为一个前导 {@code '1'}。空输入编码为空串，与 {@link HexUtil} 的空串行为一致。</p>
 */
public final class Base58 {

    /** Base58 字母表（按值 0-57 排序）。 */
    private static final char[] ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    /** 字符 → 值 查找表；仅覆盖 ASCII，非法字符为 -1。 */
    private static final byte[] INDEX = new byte[128];

    private static final BigInteger BI_58 = BigInteger.valueOf(58);

    static {
        Arrays.fill(INDEX, (byte) -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            INDEX[ALPHABET[i]] = (byte) i;
        }
    }

    private Base58() {
    }

    /**
     * 将字节数组编码为 Base58 字符串。
     *
     * @param data 要编码的字节数组，不能为 null
     * @return Base58 字符串；空数组返回空串
     */
    public static String encode(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        int zeros = 0;
        while (zeros < data.length && data[zeros] == 0) {
            zeros++;
        }
        StringBuilder sb = new StringBuilder();
        byte[] body = Arrays.copyOfRange(data, zeros, data.length);
        if (body.length > 0) {
            BigInteger num = new BigInteger(1, body);
            while (num.signum() > 0) {
                BigInteger[] qr = num.divideAndRemainder(BI_58);
                sb.append(ALPHABET[qr[1].intValue()]);
                num = qr[0];
            }
        }
        for (int i = 0; i < zeros; i++) {
            sb.append(ALPHABET[0]);
        }
        return sb.reverse().toString();
    }

    /**
     * 将字符串（UTF-8 编码）编码为 Base58 字符串。
     *
     * @param str 要编码的字符串，不能为 null
     * @return Base58 字符串
     */
    public static String encode(String str) {
        CheckUtil.notNull(str, "字符串不能为空");
        return encode(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将 Base58 字符串解码为字节数组。
     *
     * @param b58 Base58 字符串，不能为 null；空串返回空数组
     * @return 解码后的字节数组
     * @throws IllegalArgumentException 包含非法的 Base58 字符
     */
    public static byte[] decode(String b58) {
        CheckUtil.notNull(b58, "Base58 字符串不能为空");
        if (b58.isEmpty()) {
            return new byte[0];
        }
        if (!isValidBase58(b58)) {
            throw new IllegalArgumentException("包含非法的 Base58 字符: " + b58);
        }
        int zeros = 0;
        while (zeros < b58.length() && b58.charAt(zeros) == ALPHABET[0]) {
            zeros++;
        }
        BigInteger num = BigInteger.ZERO;
        for (int i = zeros; i < b58.length(); i++) {
            num = num.multiply(BI_58).add(BigInteger.valueOf(INDEX[b58.charAt(i)]));
        }
        byte[] body = num.toByteArray();
        // BigInteger.toByteArray 可能带符号位前导 0x00；全零时得到单个 0x00，需归零
        if (body.length > 1 && body[0] == 0) {
            body = Arrays.copyOfRange(body, 1, body.length);
        } else if (body.length == 1 && body[0] == 0) {
            body = new byte[0];
        }
        byte[] result = new byte[zeros + body.length];
        System.arraycopy(body, 0, result, zeros, body.length);
        return result;
    }

    /**
     * 将 Base58 字符串解码为 UTF-8 字符串。
     *
     * @param b58 Base58 字符串，不能为 null
     * @return 解码后的 UTF-8 字符串
     */
    public static String decodeToString(String b58) {
        return new String(decode(b58), StandardCharsets.UTF_8);
    }

    /**
     * 检查字符串是否为合法的 Base58 表示。
     * <p>合法要求：非 null、非空、仅包含字母表中的字符（{@code 1-9A-HJ-NP-Za-km-z}）。</p>
     *
     * @param str 待检查的字符串
     * @return 如果是合法的 Base58 字符串则返回 true
     */
    public static boolean isValidBase58(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c >= 128 || INDEX[c] < 0) {
                return false;
            }
        }
        return true;
    }
}
