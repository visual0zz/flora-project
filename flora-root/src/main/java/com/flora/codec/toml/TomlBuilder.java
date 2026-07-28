package com.flora.codec.toml;

import java.util.List;
import java.util.Map;

/**
 * TOML 序列化器（纯内存、零依赖、线程安全）。
 * <p>把 {@link Map} 写为 TOML 文本（表/表数组/键值对/内联值）。</p>
 */
public final class TomlBuilder {

    private TomlBuilder() {}

    public static String toTomlString(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        printMap(map, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void printMap(Map<String, Object> map, StringBuilder sb) {
        // 第一遍：输出标量/数组/行内表键
        boolean hasSub = false;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            String key = quoteKey(e.getKey());
            if (v instanceof Map && !((Map<Object, Object>) v).isEmpty()) {
                hasSub = true;
                continue;
            }
            if (v instanceof List && !((List<Object>) v).isEmpty()) {
                // 检查是否为表数组（List<Map>）
                List<Object> list = (List<Object>) v;
                if (list.get(0) instanceof Map) { hasSub = true; continue; }
            }
            sb.append(key).append(" = ").append(formatValue(v)).append('\n');
        }

        // 第二遍：输出子表/表数组
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            String key = quoteKey(e.getKey());
            if (v instanceof Map) {
                Map<Object, Object> sub = (Map<Object, Object>) v;
                if (sub.isEmpty()) continue;
                sb.append('\n').append('[').append(key).append("]\n");
                printFlatTable(sub, sb);
            } else if (v instanceof List) {
                List<Object> list = (List<Object>) v;
                if (list.isEmpty()) continue;
                if (list.get(0) instanceof Map) {
                    for (Object item : list) {
                        sb.append('\n').append("[[").append(key).append("]]\n");
                        printFlatTable((Map<Object, Object>) item, sb);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void printFlatTable(Map<Object, Object> table, StringBuilder sb) {
        for (Map.Entry<Object, Object> e : table.entrySet()) {
            String key = quoteKey(e.getKey().toString());
            Object v = e.getValue();
            if (v instanceof Map) {
                Map<Object, Object> sub = (Map<Object, Object>) v;
                sb.append('\n').append('[').append(key).append("]\n");
                printFlatTable(sub, sb);
            } else if (v instanceof List && !((List<Object>) v).isEmpty()
                    && ((List<Object>) v).get(0) instanceof Map) {
                for (Object item : (List<Object>) v) {
                    sb.append('\n').append("[[").append(key).append("]]\n");
                    printFlatTable((Map<Object, Object>) item, sb);
                }
            } else {
                sb.append(key).append(" = ").append(formatValue(v)).append('\n');
            }
        }
    }

    private static String quoteKey(String key) {
        if (key.isEmpty()) return "\"\"";
        // 裸键：字母/数字/下划线/连字符
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) {
                return quoteString(key);
            }
        }
        return key;
    }

    private static String formatValue(Object v) {
        if (v == null) return "false";
        if (v instanceof Boolean) return v.toString();
        if (v instanceof String) return quoteString((String) v);
        if (v instanceof Number) return v.toString();
        if (v instanceof List) return formatArray((List<?>) v);
        if (v instanceof Map) return formatInlineTable((Map<String, Object>) v);
        return quoteString(v.toString());
    }

    private static String formatArray(List<?> list) {
        if (list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatValue(list.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String formatInlineTable(Map<String, Object> map) {
        if (map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(quoteKey(e.getKey())).append(" = ").append(formatValue(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    /** 用 TOML 基本字符串引号包裹。 */
    static String quoteString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                case '\r': sb.append("\\r"); break;
                default: sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
