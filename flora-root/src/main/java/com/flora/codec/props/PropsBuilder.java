package com.flora.codec.props;

import java.util.Map;

/**
 * 嵌套 {@link Map} → .properties 文本 序列化器（纯内存、零依赖、线程安全）。
 * <p>把解析 {@link PropsParser} 产生的嵌套 {@link LinkedHashMap} 反向扁平化为点号键文本；
 * 对非 {@link Map} 的叶子值调用 {@link String#valueOf(Object)}，并对 {@code \} {@code =} {@code :}
 * 以及行首 {@code #} / {@code !} 做转义。</p>
 */
public final class PropsBuilder {

    private PropsBuilder() {}

    public static String build(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        build(map, "", sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void build(Map<String, Object> map, String prefix, StringBuilder sb) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map) {
                build((Map<String, Object>) v, key, sb);
            } else {
                sb.append(escapeKey(key))
                  .append('=')
                  .append(escapeValue(String.valueOf(v)))
                  .append('\n');
            }
        }
    }

    private static String escapeKey(String k) {
        StringBuilder sb = new StringBuilder(k.length());
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '=') sb.append("\\=");
            else if (c == ':') sb.append("\\:");
            else if ((c == '#' || c == '!') && i == 0) sb.append('\\').append(c);
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String escapeValue(String v) {
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if ((c == '#' || c == '!') && i == 0) sb.append('\\');
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
