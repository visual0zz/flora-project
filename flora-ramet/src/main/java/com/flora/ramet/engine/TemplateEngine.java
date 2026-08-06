package com.flora.ramet.engine;

import com.flora.ramet.engine.ast.Node;
import com.flora.ramet.engine.model.TemplateMeta;
import com.flora.ramet.engine.runtime.Context;
import com.flora.ramet.engine.runtime.TemplateBody;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 代码生成编排器：把模板源经过「解析 → 元数据展开 → 渲染」三步，产出生成结果。
 *
 * <p>职责边界：本类只做编排，不做具体的词法/语法/元数据解析（由 {@link Template}、
 * {@link com.flora.ramet.engine.parser.MetaParser} 负责），也不做输出文件格式化
 * （警告注释由 {@link OutputDecorator} 负责）。输入/输出均为纯数据，不涉及文件系统。
 * 子模板通过 {@link TemplateRepository} 按需拉取，入口模板与 include 走同一解析管线。</p>
 */
public final class TemplateEngine {

    private TemplateEngine() {
    }

    /**
     * 单次生成的结果：渲染后的内容 + 输出相对路径。
     */
    public record Generated(String content, String relativePath) {}

    /**
     * 解析并渲染一个模板源，处理元数据解析、变量校验、笛卡尔积展开。
     *
     * @param src     模板源（文本 + key）
     * @param repo    子模板仓库（用于 {@code <#include>}）
     * @return 生成结果列表（无 combine 时返回 1 个，有 combine 时返回笛卡尔积展开的多个）
     */
    public static List<Generated> generate(TemplateSource src, TemplateRepository repo) {
        return generate(src, repo, null);
    }

    /**
     * 便捷入口：直接解析文本（无 key），不依赖任何 include。
     */
    public static List<Generated> generate(String tplContent, TemplateRepository repo) {
        return generate(TemplateSource.of(tplContent), repo);
    }

    public static List<Generated> generate(TemplateSource src, TemplateRepository repo,
                                            String source) {
        String key = src.key();
        String srcName = source != null ? source : key;

        Template tpl = Template.parse(src);
        TemplateMeta meta = TemplateMeta.from(tpl.meta());

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
            String content = renderBody(tpl.nodes(), mergedParams, repo, srcName);
            content = OutputDecorator.decorate(content, variants.get(0).outputPath(), meta.config(), srcName);
            results.add(new Generated(content, variants.get(0).outputPath()));
        } else {
            // 多文件模式：各自渲染，同路径拼接（兼容非 Combine 导致的偶发同路径）
            Map<String, List<String>> merged = new LinkedHashMap<>();
            String lastPath = null;
            for (TemplateMeta.Variant v : variants) {
                String rawContent = renderBody(tpl.nodes(), v.params(), repo, srcName);
                String content;
                if (!v.outputPath().equals(lastPath)) {
                    lastPath = v.outputPath();
                    content = OutputDecorator.decorate(rawContent, v.outputPath(), meta.config(), srcName);
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
                                      TemplateRepository repo, String source) {
        try {
            return TemplateBody.of(nodes).render(Context.of(params, repo, source));
        } catch (java.io.IOException e) {
            throw new CodeGenException("渲染失败: " + e.getMessage(), e);
        }
    }

    /**
     * 预编译一段模板文本为 {@link Template}（解析但不展开/渲染）。
     * 主要用于把内存中的子模板装入 {@link TemplateRepository}。
     */
    public static Template precompile(String tplContent) {
        return Template.parse(TemplateSource.of(tplContent));
    }
}
