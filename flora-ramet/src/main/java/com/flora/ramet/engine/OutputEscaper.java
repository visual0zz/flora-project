package com.flora.ramet.engine;

/**
 * 输出转义工具：对渲染后的「最终输出」整体按指定方案进行转义。
 *
 * <p>转义方案通过模板 {@code @Config{ escape: "..." }} 指定，作用于整个渲染产物
 * （警告注释在转义之后注入，不会被二次转义）。当前支持：
 * <ul>
 *   <li>{@code html} — HTML 实体转义（{@code & < > " '}）</li>
 *   <li>{@code xml}  — XML 实体转义（同 html，单引号用 {@code &apos;}）</li>
 *   <li>{@code js}   — JavaScript 字符串字面量转义</li>
 *   <li>{@code none} 或未设置 — 不转义</li>
 * </ul>
 *
 * <p>注意：本实现转义的是「整段最终输出」，而非逐个插值。因此对模板中本就存在的
 * 字面量特殊字符同样生效（例如 HTML 模板里写死的 {@code <div>} 也会被转义）。
 * 这正是「对最终输出转义」的语义；若需仅转义插值内容，应改用更细粒度的方案。
 */
public final class OutputEscaper {

    private OutputEscaper() {
    }

    /**
     * 按方案转义整段输出内容。
     *
     * @param content 渲染后的原始内容（可能为 null，按空串处理）
     * @param scheme  转义方案名；{@code null}/空/{@code none} 表示不转义
     * @return 转义后的内容；方案无效时抛 {@link CodeGenException}
     */
    public static String escape(String content, String scheme) {
        if (content == null) return "";
        if (scheme == null || scheme.isEmpty() || scheme.equals("none")) {
            return content;
        }
        return switch (scheme) {
            case "html" -> escapeHtml(content);
            case "xml"  -> escapeXml(content);
            case "js"   -> escapeJs(content);
            default -> throw new CodeGenException(
                    "未知的转义方案: " + scheme + "（支持 html / xml / js / none）");
        };
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
