package com.flora.data;

import com.flora.container.ObjectUtil;
import com.flora.java.CheckUtil;
import java.util.StringJoiner;

/**
 * 字符串处理工具类。
 * <p>提供字符串的空值/空白判断、修剪、前缀后缀移除、截取、填充、重复拼接、
 * 替换、大小写转换、反转、截断、编解码及类型转换等常用操作。</p>
 */
public final class StrUtil {
    private static final String EMPTY = "";
    private static final String NULL = "null";
    private StrUtil() {
    }

    

    /**
     * 判断字符串是否为 null 或空字符串。
     *
     * @param str 待检查的字符串
     * @return 如果为 null 或空字符串则返回 true
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否为非空（不为 null 且长度大于 0）。
     *
     * @param str 待检查的字符串
     * @return 如果不为 null 且长度大于 0 则返回 true
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    /**
     * 判断字符串是否为空白（null、空串或仅含空白字符）。
     *
     * @param str 待检查的字符串
     * @return 如果为空白则返回 true
     */
    public static boolean isBlank(String str) {
        if (str == null || str.isEmpty()) {
            return true;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断字符串是否非空白（不为 null、非空串且不全是空白字符）。
     *
     * @param str 待检查的字符串
     * @return 如果非空白则返回 true
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 判断字符串是否等于 "true"（不区分大小写）。
     *
     * @param str 待检查的字符串
     * @return 如果忽略大小写等于 "true" 则返回 true
     */
    public static boolean isTrue(String str) {
        return "true".equalsIgnoreCase(str);
    }

    /**
     * 判断字符串是否等于 "false"（不区分大小写）。
     *
     * @param str 待检查的字符串
     * @return 如果忽略大小写等于 "false" 则返回 true
     */
    public static boolean isFalse(String str) {
        return "false".equalsIgnoreCase(str);
    }

    

    /**
     * 去除字符串两端的空白字符。
     *
     * @param str 待处理的字符串，可以为 null
     * @return 修剪后的字符串，null 返回 null
     */
    public static String trim(String str) {
        return str == null ? null : str.trim();
    }

    /**
     * 去除字符串两端空白，若结果为空则返回 null。
     *
     * @param str 待处理的字符串，可以为 null
     * @return 修剪后非空的字符串，否则返回 null
     */
    public static String trimToNull(String str) {
        String trimmed = trim(str);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    
    public static String trimToEmpty(String str) {
        return str == null ? EMPTY : str.trim();
    }

    

    
    public static String removePrefix(String str, String prefix) {
        if (isEmpty(str) || isEmpty(prefix)) {
            return str;
        }
        if (str.startsWith(prefix)) {
            return str.substring(prefix.length());
        }
        return str;
    }

    
    public static String removeSuffix(String str, String suffix) {
        if (isEmpty(str) || isEmpty(suffix)) {
            return str;
        }
        if (str.endsWith(suffix)) {
            return str.substring(0, str.length() - suffix.length());
        }
        return str;
    }

    

    
    public static String substring(String str, int begin) {
        CheckUtil.notNull(str, "字符串不能为空");
        if (begin < 0) {
            return substring(str, str.length() + begin);
        }
        if (begin >= str.length()) {
            return EMPTY;
        }
        return str.substring(begin);
    }

    
    public static String substring(String str, int begin, int end) {
        CheckUtil.notNull(str, "字符串不能为空");
        int len = str.length();
        int start = begin < 0 ? Math.max(len + begin, 0) : Math.min(begin, len);
        int e = end < 0 ? len + end : Math.min(end, len);
        int actualStart = Math.min(start, len);
        int actualEnd = Math.max(e, actualStart);
        return str.substring(actualStart, Math.min(actualEnd, len));
    }

    
    public static String left(String str, int length) {
        if (str == null) {
            return null;
        }
        if (length <= 0) {
            return EMPTY;
        }
        return str.substring(0, Math.min(length, str.length()));
    }

    
    public static String right(String str, int length) {
        if (str == null) {
            return null;
        }
        if (length <= 0) {
            return EMPTY;
        }
        int len = str.length();
        if (length >= len) {
            return str;
        }
        return str.substring(len - length);
    }

    

    
    public static String padLeft(String str, int length, char padChar) {
        if (str == null) {
            return null;
        }
        int pads = length - str.length();
        if (pads <= 0) {
            return str;
        }
        return String.valueOf(padChar).repeat(pads) + str;
    }

    
    public static String padRight(String str, int length, char padChar) {
        if (str == null) {
            return null;
        }
        int pads = length - str.length();
        if (pads <= 0) {
            return str;
        }
        return str + String.valueOf(padChar).repeat(pads);
    }

    

    
    public static String repeat(String str, int count) {
        if (str == null || count <= 0) {
            return EMPTY;
        }
        return str.repeat(count);
    }

    
    public static String join(String delimiter, Object... elements) {
        if (elements == null || elements.length == 0) {
            return EMPTY;
        }
        var joiner = new StringJoiner(delimiter == null ? EMPTY : delimiter);
        for (Object elem : elements) {
            joiner.add(elem == null ? NULL : elem.toString());
        }
        return joiner.toString();
    }

    
    public static <T> String join(String delimiter, Iterable<T> iterable) {
        if (iterable == null) {
            return EMPTY;
        }
        var joiner = new StringJoiner(delimiter == null ? EMPTY : delimiter);
        for (T elem : iterable) {
            joiner.add(elem == null ? NULL : elem.toString());
        }
        return joiner.toString();
    }

    

    
    public static String replace(String str, char oldChar, char newChar) {
        if (str == null) {
            return null;
        }
        return str.replace(oldChar, newChar);
    }

    
    public static String replace(String str, String target, String replacement) {
        if (str == null || target == null || target.isEmpty()) {
            return str;
        }
        String rep = replacement == null ? EMPTY : replacement;
        return str.replace(target, rep);
    }

    

    
    public static String capitalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        char first = str.charAt(0);
        if (Character.isUpperCase(first)) {
            return str;
        }
        return Character.toUpperCase(first) + str.substring(1);
    }

    
    public static String uncapitalize(String str) {
        if (isEmpty(str)) {
            return str;
        }
        char first = str.charAt(0);
        if (Character.isLowerCase(first)) {
            return str;
        }
        return Character.toLowerCase(first) + str.substring(1);
    }

    

    
    public static String reverse(String str) {
        if (str == null) {
            return null;
        }
        return new StringBuilder(str).reverse().toString();
    }

    

    
    public static String truncate(String str, int maxLen, String suffix) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLen) {
            return str;
        }
        String suf = suffix == null ? EMPTY : suffix;
        return str.substring(0, Math.max(0, maxLen - suf.length())) + suf;
    }

    

    
    public static String defaultIfNull(String str, String defaultStr) {
        return ObjectUtil.defaultIfNull(str, defaultStr);
    }

    
    public static String defaultIfEmpty(String str, String defaultStr) {
        return isEmpty(str) ? defaultStr : str;
    }

    
    public static String defaultIfBlank(String str, String defaultStr) {
        return isBlank(str) ? defaultStr : str;
    }

    

    
    public static byte[] toBytes(String str) {
        if (str == null) {
            return new byte[0];
        }
        return str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    
    public static String fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    
}
