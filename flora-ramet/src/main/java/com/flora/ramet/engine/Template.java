package com.flora.ramet.engine;

import com.flora.ramet.engine.ast.MetaNode;
import com.flora.ramet.engine.ast.Node;
import com.flora.ramet.engine.lexer.Lexer;
import com.flora.ramet.engine.lexer.WhitespaceTrimmer;
import com.flora.ramet.engine.parser.MetaParser;
import com.flora.ramet.engine.parser.Parser;
import com.flora.ramet.engine.runtime.Context;
import com.flora.ramet.engine.runtime.TemplateBody;

import java.util.List;
import java.util.Map;

/**
 * 解析后的模板——词法/语法分析管线的唯一产物。
 *
 * <p>统一持有三样东西：
 * <ul>
 *   <li>{@link #key()} —— 在 {@link TemplateRepository} 中的定位标识（入口模板即其相对路径）；</li>
 *   <li>{@link #nodes()} —— 语法树（已规整空白）；</li>
 *   <li>{@link #meta()} —— 提取出的元数据（{@code <#meta>} 块内容，无则 {@code null}）。</li>
 * </ul>
 *
 * <p>入口模板与 {@code <#include>} 子模板在解析后都表现为 {@code Template}，
 * 不再有「文本 vs 已编译产物」的区分。</p>
 */
public final class Template {

    private final String key;
    private final List<Node> nodes;
    private final MetaParser.MetaData meta;

    public Template(String key, List<Node> nodes, MetaParser.MetaData meta) {
        if (nodes == null) throw new IllegalArgumentException("nodes must not be null");
        this.key = key;
        this.nodes = nodes;
        this.meta = meta;
    }

    public String key() {
        return key;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public MetaParser.MetaData meta() {
        return meta;
    }

    /**
     * 解析模板源：Lexer → WhitespaceTrimmer → Parser → 提取元数据。
     * 元数据只允许一个块，重复块的报错由 {@link Parser} 负责。
     */
    public static Template parse(TemplateSource src) {
        List<Node> nodes = Parser.parse(WhitespaceTrimmer.trim(Lexer.lex(src.text())));
        MetaParser.MetaData meta = null;
        for (Node n : nodes) {
            if (n instanceof MetaNode m) {
                meta = m.data();
                break;
            }
        }
        return new Template(src.key(), nodes, meta);
    }

    /** 便捷入口：直接解析文本（无 key）。 */
    public static Template parse(String text) {
        return parse(TemplateSource.of(text));
    }

    /**
     * 渲染一段内联表达式文本（如 {@code @Path} 中的 {@code ${...}}），
     * 使用给定的已解析参数，不产生任何 include。
     */
    public static String render(String expr, Map<String, Object> params) {
        List<Node> nodes = parse(TemplateSource.of(expr)).nodes();
        try {
            return TemplateBody.of(nodes).render(Context.of(params, TemplateRepository.none()));
        } catch (java.io.IOException e) {
            throw new CodeGenException("路径表达式渲染失败: " + e.getMessage(), e);
        }
    }
}
