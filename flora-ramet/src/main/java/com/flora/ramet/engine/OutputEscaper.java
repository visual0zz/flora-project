package com.flora.ramet.engine;

/**
 * 输出转义工具：提供「按值」的转义原语，供内置函数 {@code html / xml / js} 在插值点调用。
 *
 * <p>转义作用于单个被插值的数值（{@code ${html(x)}}），而非整段模板输出——
 * 这样只有动态数据被转义，模板作者写死的目标代码（如 {@code <div>}、{@code List<String>}）
 * 不会被误伤。当前支持：
 * <ul>
 *   <li>{@code html} — HTML 实体转义（{@code & < > " '}）</li>
 *   <li>{@code xml}  — XML 实体转义（同 html，单引号用 {@code &apos;}）</li>
 *   <li>{@code js}   — JavaScript 字符串字面量转义</li>
 * </ul>
 */
public final class OutputEscaper {

    private OutputEscaper() {
    }

    /** HTML 实体转义：{@code & < > " '}。 */
    public static String escapeHtml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> sb.append("&amp;");
                case '<'  -> sb.append("&lt;");
                case '>'  -> sb.append("&gt;");
                case '"'  -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** XML 实体转义：{@code & < > " '}（单引号用 {@code &apos;}）。 */
    public static String escapeXml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> sb.append("&amp;");
                case '<'  -> sb.append("&lt;");
                case '>'  -> sb.append("&gt;");
                case '"'  -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** JavaScript 字符串字面量转义：反斜杠、引号、斜杠与控制字符。 */
    public static String escapeJs(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\'' -> sb.append("\\'");
                case '/'  -> sb.append("\\/");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\f' -> sb.append("\\f");
                case '\b' -> sb.append("\\b");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
