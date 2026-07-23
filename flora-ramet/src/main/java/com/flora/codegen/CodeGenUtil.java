package com.flora.codegen;

import com.flora.codegen.engine.CodeGenException;
import com.flora.codegen.engine.TemplateMeta;
import com.flora.codegen.engine.Token;
import com.flora.codegen.engine.ast.MetaNode;
import com.flora.codegen.engine.ast.Node;
import com.flora.codegen.engine.parser.Lexer;
import com.flora.codegen.engine.parser.MetaParser;
import com.flora.codegen.engine.parser.Parser;
import com.flora.codegen.engine.runtime.Context;
import com.flora.codegen.engine.runtime.TemplateBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 代码生成门面：组合模板引擎各部件完成一次生成的编排。
 *
 * <p>职责边界：本类只做「编排」，不做任何具体的词法/语法/元数据解析或渲染：
 * <ul>
 *   <li>{@link com.flora.codegen.engine.Token} —— 词法分析（文本 → Token）</li>
 *   <li>{@link com.flora.codegen.engine.ast.Node} —— 语法分析（Token → AST）</li>
 *   <li>{@link com.flora.codegen.engine.parser.MetaParser} —— 元数据文本解析（@Param/@Cartesian/@Path/@Config）</li>
 *   <li>{@link com.flora.codegen.engine.TemplateMeta} —— 模板级元数据聚合与笛卡尔积</li>
 *   <li>{@link com.flora.codegen.engine.runtime.TemplateBody} —— AST 渲染（Context → 文本）</li>
 * </ul>
 * 输入与输出都是纯数据，不涉及文件系统；文件读写由 {@link Ramet} 负责。
 */
public final class CodeGenUtil {

    private CodeGenUtil() {
    }

    /**
     * 单次生成的结果：渲染后的内容 + 输出相对路径。
     */
    public record Generated(String content, String relativePath) {}

    /**
     * 解析并渲染一个模板文件，处理元数据解析、变量校验、笛卡尔积展开。
     * 输入和输出都是纯数据，不涉及文件系统。
     *
     * @param tplContent 模板文件的全文
     * @param includes   所有可被 include 的模板（已预编译），key 为相对路径
     * @return 生成结果列表（无 combine 时返回 1 个，有 combine 时返回笛卡尔积展开的多个）
     * @throws CodeGenException 元数据解析失败、变量校验失败等
     */
    public static List<Generated> generate(String tplContent, Map<String, CompiledTemplate> includes) {
        return generate(tplContent, includes, null, null);
    }

    public static List<Generated> generate(String tplContent, Map<String, CompiledTemplate> includes, String source) {
        return generate(tplContent, includes, source, null);
    }

    public static List<Generated> generate(String tplContent, Map<String, CompiledTemplate> includes,
                                            String source, String templatesRoot) {
        List<Token> toks = Lexer.lex(tplContent);
        List<Node> nodes = Parser.parse(toks);

        // 只允许一个 meta 块
        List<Node> metaNodes = nodes.stream()
                .filter(n -> n instanceof MetaNode)
                .toList();
        if (metaNodes.size() > 1) {
            throw new CodeGenException("只允许一个 meta 块，实际发现 " + metaNodes.size() + " 个");
        }
        MetaParser.MetaData md = metaNodes.isEmpty()
                ? new MetaParser.MetaData(null, null, null, null, null)
                : ((MetaNode) metaNodes.get(0)).data();
        TemplateMeta meta = TemplateMeta.from(md);

        List<Generated> results = new ArrayList<>();
        List<TemplateMeta.Variant> variants = meta.expand();

        if (variants.isEmpty()) {
            return results;
        }

        // 检查是否所有 Variant 路径相同（单文件输出模式）
        boolean allSamePath = variants.stream()
                .map(TemplateMeta.Variant::outputPath)
                .distinct().count() <= 1;

        if (allSamePath && variants.size() > 1) {
            // 单文件模式：合并所有 Variant 的 Combine 轴值为列表，只渲染一次
            Map<String, Object> mergedParams = new LinkedHashMap<>(variants.get(0).params());
            // 收集每个 key 在所有 Variant 中的值
            Map<String, List<Object>> allValues = new LinkedHashMap<>();
            for (TemplateMeta.Variant v : variants) {
                for (Map.Entry<String, Object> entry : v.params().entrySet()) {
                    allValues.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
                }
            }
            // 值不唯一的 key 即为 Combine 轴，用列表替换原来的单值
            for (Map.Entry<String, List<Object>> entry : allValues.entrySet()) {
                if (new LinkedHashSet<>(entry.getValue()).size() > 1) {
                    mergedParams.put(entry.getKey(), entry.getValue());
                }
            }
            String content = renderBody(nodes, mergedParams, includes, source, templatesRoot);
            content = applyAutoWarning(content, variants.get(0).outputPath(), meta.config(), source);
            results.add(new Generated(content, variants.get(0).outputPath()));
        } else {
            // 多文件模式：各自渲染，同路径拼接（兼容非 Combine 导致的偶发同路径）
            Map<String, List<String>> merged = new LinkedHashMap<>();
            String lastPath = null;
            for (TemplateMeta.Variant v : variants) {
                String rawContent = renderBody(nodes, v.params(), includes, source, templatesRoot);
                String content;
                if (!v.outputPath().equals(lastPath)) {
                    lastPath = v.outputPath();
                    content = applyAutoWarning(rawContent, v.outputPath(), meta.config(), source);
                } else {
                    content = rawContent;
                }
                merged.computeIfAbsent(v.outputPath(), k -> new ArrayList<>()).add(content);
            }
            for (Map.Entry<String, List<String>> entry : merged.entrySet()) {
                results.add(new Generated(String.join("", entry.getValue()), entry.getKey()));
            }
        }
        return results;
    }

    /** 渲染 Node 列表。 */
    private static String renderBody(List<Node> nodes, Map<String, Object> params,
                                      Map<String, CompiledTemplate> includes, String source,
                                      String templatesRoot) {
        try {
            return TemplateBody.of(nodes).render(Context.of(params, includes, source, templatesRoot));
        } catch (IOException e) {
            throw new CodeGenException("渲染失败: " + e.getMessage(), e);
        }
    }

    // ---- 自动生成警告注释 ----

    /** 注释风格枚举。 */
    private enum CommentStyle {
        BLOCK,      // /* ... */
        LINE_HASH,  // # ...
        HTML,       // <!-- ... -->
        LINE_DASH,  // -- ...
        PERCENT;    // % ...
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
     * 根据模板路径生成警告注释的各行文本。
     *
     * @param templatePath 模板文件的相对路径（如 "tuple/Tuple.ftl"），null 表示未知
     */
    private static String[] buildWarningLines(String templatePath) {
        String templateName = templatePath != null ? templatePath : "<unknown>";
        return new String[] {
                "WARNING: Do NOT edit manually. auto generated by flora-ramet.",
                "edit \"" + templateName + "\" and regenerate instead."
        };
    }

    /**
     * 根据文件扩展名生成自动警告注释并拼接到内容头部。
     * 若扩展名未知或 @Config 中关闭了 autoWarning，则原样返回。
     *
     * @param templatePath 模板文件的相对路径，用于警告文本（null 表示未知）
     */
    private static String applyAutoWarning(String content, String outputPath,
                                            Map<String, Object> config, String templatePath) {
        // @Config{ autoWarning: false } → 跳过
        if (config != null && Boolean.FALSE.equals(config.get(ConfigKey.AUTO_WARNING.key()))) {
            return content;
        }

        // 取扩展名
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

    /**
     * 从模板源码预编译：一次 Lexer → Parser，返回 CompiledTemplate。
     * 用于 include 缓存场景。元数据解析由入口模板的 {@link #generate} 负责。
     */
    public static CompiledTemplate precompile(String tplContent) {
        return precompile(tplContent, null);
    }

    public static CompiledTemplate precompile(String tplContent, String source) {
        List<Token> toks = Lexer.lex(tplContent);
        List<Node> nodes = Parser.parse(toks);
        return new CompiledTemplate(nodes);
    }
}
