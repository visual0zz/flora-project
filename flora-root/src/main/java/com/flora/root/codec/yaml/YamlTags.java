package com.flora.root.codec.yaml;

import java.math.BigDecimal;

/**
 * YAML 标准标签（{@code !!str / !!int / !!float / !!bool / !!null / !!map / !!seq / !!timestamp}）解析。
 * <p>未知标签（如 {@code !local}）原样返回，不做类型构造（属本实现的「范围边界」，见计划文档）。</p>
 */
final class YamlTags {

    private YamlTags() {}

    static Object resolve(String tag, Object value) {
        if (tag == null) return value;
        String local = localName(tag);
        switch (local) {
            case "str":
                return value == null ? null : value.toString();
            case "int":
                return YamlParser.parseInteger(value == null ? "" : value.toString());
            case "float":
                return parseFloatValue(value == null ? "" : value.toString());
            case "bool":
                return parseBoolValue(value == null ? "" : value.toString());
            case "null":
                return null;
            case "map":
            case "seq":
            case "timestamp":
                return value; // 集合/时间戳保持原样（时间戳以字符串呈现）
            default:
                return value; // 未知标签原样返回
        }
    }

    private static String localName(String tag) {
        int colon = tag.lastIndexOf(':');
        String name = colon >= 0 ? tag.substring(colon + 1) : tag;
        int bang = name.lastIndexOf('!');
        if (bang >= 0) name = name.substring(bang + 1);
        return name;
    }

    private static Object parseFloatValue(String s) {
        if (s.isEmpty()) return null;
        switch (s) {
            case ".inf": case ".Inf": case ".INF":
                return Double.POSITIVE_INFINITY;
            case "-.inf": case "-.Inf": case "-.INF":
                return Double.NEGATIVE_INFINITY;
            case ".nan": case ".NaN": case ".NAN":
                return Double.NaN;
            default:
                return new BigDecimal(s);
        }
    }

    private static Object parseBoolValue(String s) {
        if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
        throw new IllegalStateException("YAML 标签 !!bool 的值非法: " + s);
    }
}
