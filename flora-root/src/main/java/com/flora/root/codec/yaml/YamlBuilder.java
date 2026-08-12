package com.flora.root.codec.yaml;

import java.util.List;
import java.util.Map;

/**
 * YAML 序列化器（纯内存、零依赖、线程安全）。
 * <p>把 {@link Map}/{@link List}/标量 写为 YAML 文本（块映射/块序列、必要处加引号）。</p>
 */
public final class YamlBuilder {

    private YamlBuilder() {}

    public static String toYamlString(Object obj) {
        StringBuilder sb = new StringBuilder();
        if (obj instanceof Map) {
            printMap((Map<?, ?>) obj, 0, sb);
        } else if (obj instanceof List) {
            printSeq((List<?>) obj, 0, sb);
        } else {
            sb.append(scalar(obj)).append('\n');
        }
        return sb.toString();
    }

    private static String pad(int indent) {
        StringBuilder sb = new StringBuilder(indent * 2);
        for (int i = 0; i < indent; i++) sb.append("  ");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void printMap(Map<?, ?> map, int indent, StringBuilder sb) {
        String p = pad(indent);
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String key = scalar(e.getKey());
            Object v = e.getValue();
            if (v instanceof Map && !((Map<Object, Object>) v).isEmpty()) {
                sb.append(p).append(key).append(":\n");
                printMap((Map<Object, Object>) v, indent + 1, sb);
            } else if (v instanceof List && !((List<Object>) v).isEmpty()) {
                sb.append(p).append(key).append(":\n");
                printSeq((List<Object>) v, indent + 1, sb);
            } else if (v instanceof Map || v instanceof List) {
                sb.append(p).append(key).append(": ").append(v instanceof Map ? "{}" : "[]").append('\n');
            } else {
                sb.append(p).append(key).append(": ").append(scalar(v)).append('\n');
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void printSeq(List<?> list, int indent, StringBuilder sb) {
        for (Object item : list) {
            if (item instanceof Map && !((Map<Object, Object>) item).isEmpty()) {
                printMapWithDash((Map<Object, Object>) item, indent, sb);
            } else if (item instanceof List && !((List<Object>) item).isEmpty()) {
                sb.append(pad(indent)).append("- ");
                printSeq((List<Object>) item, indent + 1, sb);
            } else {
                sb.append(pad(indent)).append("- ").append(scalar(item)).append('\n');
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void printMapWithDash(Map<Object, Object> m, int indent, StringBuilder sb) {
        String p = pad(indent);
        String cont = pad(indent + 1);
        boolean first = true;
        for (Map.Entry<Object, Object> e : m.entrySet()) {
            String key = scalar(e.getKey());
            Object v = e.getValue();
            String prefix = first ? (p + "- ") : cont;
            first = false;
            if (v instanceof Map && !((Map<Object, Object>) v).isEmpty()) {
                sb.append(prefix).append(key).append(":\n");
                printMap((Map<Object, Object>) v, indent + 2, sb);
            } else if (v instanceof List && !((List<Object>) v).isEmpty()) {
                sb.append(prefix).append(key).append(":\n");
                printSeq((List<Object>) v, indent + 2, sb);
            } else if (v instanceof Map || v instanceof List) {
                sb.append(prefix).append(key).append(": ").append(v instanceof Map ? "{}" : "[]").append('\n');
            } else {
                sb.append(prefix).append(key).append(": ").append(scalar(v)).append('\n');
            }
        }
    }

    private static String scalar(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return quoteIfNeeded((String) v);
        if (v instanceof Boolean) return ((Boolean) v) ? "true" : "false";
        return String.valueOf(v);
    }

    private static boolean isReservedWord(String s) {
        return s.equals("true") || s.equals("false") || s.equals("null") || s.equals("~")
                || s.equals("yes") || s.equals("no") || s.equals("on") || s.equals("off");
    }

    private static String quoteIfNeeded(String s) {
        if (s.isEmpty()) return "\"\"";
        if (isReservedWord(s)) return doubleQuote(s);
        if (looksNumeric(s)) return doubleQuote(s);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '/' || c == '-' || c == ':')) {
                return doubleQuote(s);
            }
        }
        return s;
    }

    private static boolean looksNumeric(String s) {
        int i = 0;
        if (s.charAt(0) == '-' || s.charAt(0) == '+') i = 1;
        if (i == s.length()) return false;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static String doubleQuote(String s) {
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
