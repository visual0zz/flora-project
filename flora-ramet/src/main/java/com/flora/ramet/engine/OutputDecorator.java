package com.flora.ramet.engine;

import java.util.Map;

/**
 * 输出文件装饰器：把「此文件由模板生成」的警告注释注入到渲染产物头部。
 *
 * <p>这是纯粹的「输出格式化」职责，与解析/渲染编排无关，故从
 * {@code TemplateEngine} 中拆出独立成类。注释风格按输出文件扩展名选择
 * （{@code /* *&#47;}、{@code # }、{@code <!-- -->}、{@code -- }、{@code % }），
 * 并尊重首行 shebang（{@code #!}）需保留在文件最开头。</p>
 */
public final class OutputDecorator {

    private OutputDecorator() {
    }

    /** 注释风格枚举。 */
    private enum CommentStyle {
        BLOCK,      // /* ... */
        LINE_HASH,  // # ...
        HTML,       // <!-- ... -->
        LINE_DASH,  // -- ...
        PERCENT     // % ...
    }

    /** 扩展名 → 注释风格映射。 */
    private static final Map<String, CommentStyle> EXTENSION_STYLES = Map.<String, CommentStyle>ofEntries(
            // BLOCK 风格
            Map.entry("java", CommentStyle.BLOCK),
            Map.entry("js", CommentStyle.BLOCK),
            Map.entry("mjs", CommentStyle.BLOCK),
            Map.entry("cjs", CommentStyle.BLOCK),
            Map.entry("ts", CommentStyle.BLOCK),
            Map.entry("tsx", CommentStyle.BLOCK),
            Map.entry("jsx", CommentStyle.BLOCK),
            Map.entry("c", CommentStyle.BLOCK),
            Map.entry("h", CommentStyle.BLOCK),
            Map.entry("cpp", CommentStyle.BLOCK),
            Map.entry("hpp", CommentStyle.BLOCK),
            Map.entry("cxx", CommentStyle.BLOCK),
            Map.entry("hxx", CommentStyle.BLOCK),
            Map.entry("cc", CommentStyle.BLOCK),
            Map.entry("hh", CommentStyle.BLOCK),
            Map.entry("cs", CommentStyle.BLOCK),
            Map.entry("go", CommentStyle.BLOCK),
            Map.entry("rs", CommentStyle.BLOCK),
            Map.entry("kt", CommentStyle.BLOCK),
            Map.entry("kts", CommentStyle.BLOCK),
            Map.entry("scala", CommentStyle.BLOCK),
            Map.entry("swift", CommentStyle.BLOCK),
            Map.entry("dart", CommentStyle.BLOCK),
            Map.entry("groovy", CommentStyle.BLOCK),
            Map.entry("gradle", CommentStyle.BLOCK),
            Map.entry("css", CommentStyle.BLOCK),
            Map.entry("scss", CommentStyle.BLOCK),
            Map.entry("less", CommentStyle.BLOCK),
            Map.entry("sass", CommentStyle.BLOCK),

            // LINE_HASH 风格
            Map.entry("py", CommentStyle.LINE_HASH),
            Map.entry("rb", CommentStyle.LINE_HASH),
            Map.entry("sh", CommentStyle.LINE_HASH),
            Map.entry("bash", CommentStyle.LINE_HASH),
            Map.entry("zsh", CommentStyle.LINE_HASH),
            Map.entry("yml", CommentStyle.LINE_HASH),
            Map.entry("yaml", CommentStyle.LINE_HASH),
            Map.entry("toml", CommentStyle.LINE_HASH),
            Map.entry("cfg", CommentStyle.LINE_HASH),
            Map.entry("ini", CommentStyle.LINE_HASH),
            Map.entry("properties", CommentStyle.LINE_HASH),
            Map.entry("env", CommentStyle.LINE_HASH),
            Map.entry("Makefile", CommentStyle.LINE_HASH),

            // HTML 风格
            Map.entry("html", CommentStyle.HTML),
            Map.entry("xhtml", CommentStyle.HTML),
            Map.entry("htm", CommentStyle.HTML),
            Map.entry("xml", CommentStyle.HTML),
            Map.entry("svg", CommentStyle.HTML),
            Map.entry("xsd", CommentStyle.HTML),
            Map.entry("wsdl", CommentStyle.HTML),
            Map.entry("xslt", CommentStyle.HTML),
            Map.entry("jsp", CommentStyle.HTML),
            Map.entry("jspx", CommentStyle.HTML),
            Map.entry("gsp", CommentStyle.HTML),
            Map.entry("vue", CommentStyle.HTML),
            Map.entry("svelte", CommentStyle.HTML),

            // LINE_DASH 风格
            Map.entry("sql", CommentStyle.LINE_DASH),

            // PERCENT 风格
            Map.entry("tex", CommentStyle.PERCENT),
            Map.entry("sty", CommentStyle.PERCENT),
            Map.entry("cls", CommentStyle.PERCENT),
            Map.entry("bib", CommentStyle.PERCENT)
    );

    /**
     * 根据输出路径的扩展名生成自动警告注释并拼接到内容头部。
     * 若扩展名未知或 @Config 中关闭了 autoWarning，则原样返回。
     *
     * @param content      渲染后的内容
     * @param outputPath   输出文件相对路径，用于判断注释风格（null 视为未知）
     * @param config       @Config 配置（null 视为未关闭）
     * @param templatePath 模板相对路径，用于警告文本（null 表示未知）
     */
    public static String decorate(String content, String outputPath,
                                   Map<String, Object> config, String templatePath) {
        // @Config{ autoWarning: false } → 跳过
        if (config != null && Boolean.FALSE.equals(config.get(ConfigKey.AUTO_WARNING.key()))) {
            return content;
        }

        // 取扩展名
        if (outputPath == null) return content;
        int dot = outputPath.lastIndexOf('.');
        if (dot < 0) return content;
        String ext = outputPath.substring(dot + 1).toLowerCase();

        CommentStyle style = EXTENSION_STYLES.get(ext);
        if (style == null) return content;  // 未知扩展名 → 跳过

        return switch (style) {
            case BLOCK -> prependBlockComment(content, templatePath);
            case LINE_HASH -> prependLineComment(content, "# ", templatePath);
            case HTML -> prependHtmlComment(content, templatePath);
            case LINE_DASH -> prependLineComment(content, "-- ", templatePath);
            case PERCENT -> prependLineComment(content, "% ", templatePath);
        };
    }

    /** 生成警告文本各行。 */
    private static String[] buildWarningLines(String templatePath) {
        String templateName = templatePath != null ? templatePath : "<unknown>";
        return new String[] {
                "WARNING: Do NOT edit manually. auto generated by flora-ramet.",
                "edit \"" + templateName + "\" and regenerate instead."
        };
    }

    /** 生成 /* ... *&#47; 风格注释。 */
    private static String prependBlockComment(String content, String templatePath) {
        StringBuilder sb = new StringBuilder();
        sb.append("/*\n");
        for (String line : buildWarningLines(templatePath)) {
            sb.append(" * ").append(line).append('\n');
        }
        sb.append(" */\n");
        return insertAfterShebang(sb.toString(), content);
    }

    /** 生成行前缀风格注释（# / -- / %）。 */
    private static String prependLineComment(String content, String prefix, String templatePath) {
        StringBuilder sb = new StringBuilder();
        for (String line : buildWarningLines(templatePath)) {
            sb.append(prefix).append(line).append('\n');
        }
        return insertAfterShebang(sb.toString(), content);
    }

    /** 生成 &lt;!-- ... --&gt; 风格注释。 */
    private static String prependHtmlComment(String content, String templatePath) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!--\n");
        for (String line : buildWarningLines(templatePath)) {
            sb.append("  ").append(line).append('\n');
        }
        sb.append("-->\n");
        return insertAfterShebang(sb.toString(), content);
    }

    /**
     * 将警告文本插入到内容中。如果内容以 shebang (#!) 开头，则插到 shebang 行之后，
     * 保持 shebang 在首行以确保 OS 内核正确识别；否则直接拼接到最顶部。
     */
    private static String insertAfterShebang(String warning, String content) {
        if (content.startsWith("#!")) {
            int nl = content.indexOf('\n');
            if (nl >= 0) {
                return content.substring(0, nl + 1) + warning + content.substring(nl + 1);
            }
            // 只有 shebang 一行且没有换行
            return content + '\n' + warning;
        }
        return warning + content;
    }
}
