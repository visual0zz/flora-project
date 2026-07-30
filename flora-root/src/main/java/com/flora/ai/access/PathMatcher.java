package com.flora.ai.access;

/**
 * 路径匹配器。
 * <p>纯算法：支持 glob、前缀、精确匹配。</p>
 */
public class PathMatcher {

    private PathMatcher() {}

    /** glob 风格匹配（仅支持 * 通配符）。 */
    public static boolean globMatch(String pattern, String path) {
        if (pattern == null || path == null) return false;
        String regex = pattern
                .replace(".", "\\.")
                .replace("**", ".+?")
                .replace("*", "[^/]+")
                .replace("?", ".");
        return path.matches(regex);
    }

    /** 前缀匹配。 */
    public static boolean prefixMatch(String prefix, String path) {
        if (prefix == null || path == null) return false;
        return path.startsWith(prefix);
    }

    /** 精确匹配。 */
    public static boolean exactMatch(String pattern, String path) {
        if (pattern == null || path == null) return false;
        return pattern.equals(path);
    }

    /** 通用匹配：先精确，再前缀，再 glob。 */
    public static boolean match(String pattern, String path) {
        if (exactMatch(pattern, path)) return true;
        if (prefixMatch(pattern, path)) return true;
        return globMatch(pattern, path);
    }
}
