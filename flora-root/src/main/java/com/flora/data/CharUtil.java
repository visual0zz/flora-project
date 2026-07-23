package com.flora.data;


/**
 * 字符工具类，提供字符和字符串的常见类型判断与转换操作。
 */
public final class CharUtil {

    private CharUtil() {
    }

    /**
     * 判断字符是否为 ASCII 字符（码值小于 128）。
     *
     * @param ch 待判断字符
     * @return 如果是 ASCII 字符返回 true
     */
    public static boolean isAscii(char ch) {
        return ch < 128;
    }

    /**
     * 判断字符是否为字母。
     *
     * @param ch 待判断字符
     * @return 如果是字母返回 true
     */
    public static boolean isLetter(char ch) {
        return Character.isLetter(ch);
    }

    /**
     * 判断字符是否为数字。
     *
     * @param ch 待判断字符
     * @return 如果是数字返回 true
     */
    public static boolean isNumber(char ch) {
        return Character.isDigit(ch);
    }

    /**
     * 判断字符是否为字母或数字。
     *
     * @param ch 待判断字符
     * @return 如果是字母或数字返回 true
     */
    public static boolean isLetterOrNumber(char ch) {
        return Character.isLetterOrDigit(ch);
    }

    /**
     * 判断字符是否为空白字符。
     *
     * @param ch 待判断字符
     * @return 如果是空白字符返回 true
     */
    public static boolean isWhitespace(char ch) {
        return Character.isWhitespace(ch);
    }

    /**
     * 判断字符是否为大写字母。
     *
     * @param ch 待判断字符
     * @return 如果是大写字母返回 true
     */
    public static boolean isUpperCase(char ch) {
        return Character.isUpperCase(ch);
    }

    /**
     * 判断字符是否为小写字母。
     *
     * @param ch 待判断字符
     * @return 如果是小写字母返回 true
     */
    public static boolean isLowerCase(char ch) {
        return Character.isLowerCase(ch);
    }

    /**
     * 将字符转换为大写。
     *
     * @param ch 待转换字符
     * @return 对应的大写字符
     */
    public static char toUpperCase(char ch) {
        return Character.toUpperCase(ch);
    }

    /**
     * 将字符转换为小写。
     *
     * @param ch 待转换字符
     * @return 对应的小写字符
     */
    public static char toLowerCase(char ch) {
        return Character.toLowerCase(ch);
    }

    /**
     * 判断字符串是否全部由字母组成。
     *
     * @param str 待判断字符串
     * @return 如果全部是字母返回 true，字符串为空或 null 返回 false
     */
    public static boolean isAllLetter(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isLetter(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断字符串是否全部由数字组成。
     *
     * @param str 待判断字符串
     * @return 如果全部是数字返回 true，字符串为空或 null 返回 false
     */
    public static boolean isAllNumber(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将字符转换为字符串。
     *
     * @param ch 字符
     * @return 字符对应的字符串
     */
    public static String toString(char ch) {
        return String.valueOf(ch);
    }
}
