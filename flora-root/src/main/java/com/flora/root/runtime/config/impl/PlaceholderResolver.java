package com.flora.root.runtime.config.impl;

import com.flora.root.runtime.config.ConfigException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 占位符解析工具：展开配置值中的 {@code ${key}} 引用。
 * <p>查找由调用方提供的 {@code lookup} 完成（通常是合并后的配置树 + 环境变量 + 系统属性）。
 * 引用不存在的 key 抛 {@link ConfigException}；嵌套引用递归展开，超过 {@link #MAX_DEPTH}
 * 视为疑似循环引用并报错。</p>
 */
public final class PlaceholderResolver {

    /** 嵌套/循环引用的最大展开深度。 */
    public static final int MAX_DEPTH = 16;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]*)\\}");

    private PlaceholderResolver() {}

    /** 递归解析树中所有字符串叶子值的占位符，返回新树（不修改入参）。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolveTree(Map<String, Object> tree, Function<String, String> lookup) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : tree.entrySet()) {
            result.put(e.getKey(), resolveValue(e.getValue(), lookup));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object resolveValue(Object value, Function<String, String> lookup) {
        if (value instanceof String s) {
            return resolve(s, lookup, 0);
        }
        if (value instanceof Map) {
            return resolveTree((Map<String, Object>) value, lookup);
        }
        if (value instanceof List) {
            List<Object> list = new ArrayList<>(((List<?>) value).size());
            for (Object item : (List<?>) value) {
                list.add(resolveValue(item, lookup));
            }
            return list;
        }
        return value;
    }

    /** 解析单个字符串中的占位符。 */
    public static String resolve(String value, Function<String, String> lookup) {
        return resolve(value, lookup, 0);
    }

    private static String resolve(String value, Function<String, String> lookup, int depth) {
        if (value == null || value.indexOf("${") < 0) return value;
        if (depth > MAX_DEPTH) {
            throw new ConfigException("占位符嵌套超过最大深度 " + MAX_DEPTH + "，疑似循环引用: " + value);
        }
        Matcher m = PLACEHOLDER.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1).trim();
            String resolved = lookup.apply(key);
            if (resolved == null) {
                throw new ConfigException("占位符引用的 key 不存在: ${" + key + "}");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(resolve(resolved, lookup, depth + 1)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
