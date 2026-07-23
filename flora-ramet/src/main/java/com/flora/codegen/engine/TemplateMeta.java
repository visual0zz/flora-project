package com.flora.codegen.engine;

import com.flora.codegen.TemplateFunction;
import com.flora.codegen.engine.ast.Node;
import com.flora.codegen.engine.parser.Lexer;
import com.flora.codegen.engine.parser.Lson;
import com.flora.codegen.engine.parser.MetaParser;
import com.flora.codegen.engine.parser.Parser;
import com.flora.codegen.engine.runtime.Context;
import com.flora.codegen.engine.runtime.FunctionRegistry;
import com.flora.codegen.engine.runtime.RefResolver;
import com.flora.codegen.engine.runtime.TemplateBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板元数据聚合视图：从原? {@link MetaParser.MetaData} 展开笛卡尔积并解析所有引用?
 *
 * <p>职责边界?
 * <ul>
 *   <li>{@link MetaParser} —? 负责「meta 文本 ? {@link MetaParser.MetaData}」的解析?</li>
 *   <li>本类 —? 负责笛卡尔积展开与全量引用解析?</li>
 *   <li>{@link RefResolver} —? 单值命名空间的引用解析引擎?</li>
 * </ul>
 *
 * <p>解析流程（三步）?
 * <ol>
 *   <li>{@link #from(MetaParser.MetaData)} 构建原始命名空间（@Param）和
 *       原始 @Combine 轴，不做任何引用解析?</li>
 *   <li>{@link #expand()} 先对 @Cartesian 做笛卡尔积，每个变体独立构造完整命名空?
 *       （原始命名空? + 该变体的轴值），然后通过 {@link RefResolver#resolve} 全量解析?</li>
 *   <li>每个变体独立求? @Path 表达式，最后检测路径重复?</li>
 * </ol>
 */
public final class TemplateMeta {

    /** 单一展开结果：完整参? + 已解析的输出路径? */
    public record Variant(Map<String, Object> params, String outputPath) {}

    /** &#064;Param  的原? Lson AST（未经过任何引用解析）? */
    private final Map<String, Object> rawNs;

    /** &#064;Cartesian  的原始轴值（轴名 ? 原始 Lson 值，可以? List ? FunctionCall）? */
    private final Map<String, Object> rawCombine;

    /** &#064;Path  的原? Lson 表达式（String/FunctionCall/Reference/...，可能为 null）? */
    private final Object pathExpr;

    /** &#064;Config  的原始配置映射? */
    private final Map<String, Object> config;

    /** &#064;SkipWhen  ? Lson 表达式（布尔条件，为 true 时跳过本次生成）? */
    private final Object skipExpr;

    private TemplateMeta(Map<String, Object> rawNs,
                          Map<String, Object> rawCombine,
                          Object pathExpr,
                          Map<String, Object> config,
                          Object skipExpr) {
        this.rawNs = rawNs;
        this.rawCombine = rawCombine;
        this.pathExpr = pathExpr;
        this.config = config;
        this.skipExpr = skipExpr;
    }

    /**
     * 从原? MetaData 构建 TemplateMeta（不做引用解析，只记录原? AST）?
     */
    public static TemplateMeta from(MetaParser.MetaData md) {
        Map<String, Object> rawNs = new LinkedHashMap<>();
        if (md.param() != null) rawNs.putAll(md.param());

        // 提取 @Combine 轴值（保持原始 Lson 形态，可以? List ? FunctionCall?
        Map<String, Object> rawCombine = null;
        if (md.cartesian() != null) {
            rawCombine = new LinkedHashMap<>(md.cartesian());
        }

        return new TemplateMeta(rawNs, rawCombine, md.pathValue(), md.config(), md.skipWhen());
    }

    /** 返回 @Config 的配置映射? */
    public Map<String, Object> config() {
        return config;
    }

    // ---- 笛卡尔积展开与全量解? ----

    /**
     * 展开笛卡尔积，返回所有展开结果（参数组? + 已解析的输出路径）?
     *
     * <p>流程?
     * <ol>
     *   <li>若无 combine ? 直接解析原始命名空间，求? @Path?</li>
     *   <li>若有 combine ? 先做笛卡尔积，每个变体独立构建命名空?
     *       （原? @Param + 当前变体的轴值），全量解析后求? @Path?</li>
     *   <li>路径重复检测?</li>
     * </ol>
     *
     * @return 所有展开结果
     * @throws CodeGenException 引用目标不存在、路径冲突或缺少 @Path 时抛?
     */
    public List<Variant> expand() {
        FunctionRegistry functions = new FunctionRegistry();

        // ? @Path 声明 ? 跳过输出（用于被 include 的片段模板）
        if (pathExpr == null) {
            return List.of();
        }

        // 先解? @Param 命名空间，供 Combine 轴函数参数引用查?
        Map<String, Object> baseResolved = RefResolver.resolve(rawNs, functions);

        // ? combine ? 单变?
        if (rawCombine == null || rawCombine.isEmpty()) {
            if (evalSkip(baseResolved, functions)) {
                return List.of();
            }
            String path = resolvePathExpr(baseResolved, functions);
            return List.of(new Variant(baseResolved, path));
        }

        // 提取轴名并求值轴值（支持 List 字面量和函数调用?
        List<String> axisNames = new ArrayList<>(rawCombine.keySet());
        List<List<Object>> axisValues = new ArrayList<>();
        for (String name : axisNames) {
            axisValues.add(evalCombineAxis(rawCombine.get(name), functions, baseResolved));
        }

        // 笛卡尔积
        List<Variant> results = new ArrayList<>();

        for (Map<String, Object> combo : cartesian(axisNames, axisValues)) {
            // 1) 构建当前变体的完整命名空间（原始 @Param + 当前轴值）
            Map<String, Object> fullNs = new LinkedHashMap<>(rawNs.size() + combo.size());
            fullNs.putAll(rawNs);
            fullNs.putAll(combo);

            // 2) 全量解析引用（所有值均为单值，无需 combine 感知?
            Map<String, Object> resolved = RefResolver.resolve(fullNs, functions);

            // 3) @SkipWhen 求值：跳过此变?
            if (evalSkip(resolved, functions)) continue;

            // 4) 求? @Path
            String path = resolvePathExpr(resolved, functions);

            results.add(new Variant(resolved, path));
        }
        return results;
    }

    /**
     * 求? Combine 轴的原始值为具体列表?
     *
     * <ul>
     *   <li>{@link List} ? 直接返回（向后兼容，引用由后? {@link RefResolver#resolve} 解析?</li>
     *   <li>{@link Lson.FunctionCall} ? 先解析参数中的引用和嵌套函数调用?
     *       再求值函数调用（? {@code range(2, 30)} ?
     *       {@code selfCrossProduct([I, L, F, D], 2)}），结果必须为列?</li>
     * </ul>
     *
     * @param raw         Lson 原始?
     * @param functions   函数注册?
     * @param resolvedNs  已解析的 @Param 命名空间（用于解析轴函数参数中的引用?
     * @return 具体的轴值列表（? Java 值，? Reference?
     */
    @SuppressWarnings("unchecked")
    private static List<Object> evalCombineAxis(Object raw, FunctionRegistry functions,
                                                Map<String, Object> resolvedNs) {
        if (raw instanceof List<?> list) {
            // 列表字面量保持原样（引用由后? RefResolver.resolve(fullNs) 解析?
            return (List<Object>) list;
        }
        if (raw instanceof Lson.FunctionCall(String name, List<Object> args1)) {
            TemplateFunction fn = functions.get(name);
            if (fn == null) {
                throw new CodeGenException("Combine 轴中未找到函?: " + name);
            }
            // 解析参数中的引用和嵌套函数调用（? [I,L,F,D] ? 真实类型对象?
            List<Object> args = new ArrayList<>(args1.size());
            for (Object arg : args1) {
                args.add(RefResolver.eval(arg, resolvedNs, functions));
            }
            // 值已求值，包装为惰? arg 适配接口变更
            List<com.flora.codegen.LazyArg> lazyArgs = new ArrayList<>(args.size());
            for (Object a : args) {
                lazyArgs.add(() -> a);
            }
            Object result = fn.apply(lazyArgs);
            if (result instanceof List<?> resultList) {
                return (List<Object>) resultList;
            }
            throw new CodeGenException("Combine 轴的函数 '" + name + "' 必须返回列表");
        }
        throw new CodeGenException("Combine 轴值必须是数组或函数调用，实际?: "
                + (raw == null ? "null" : raw.getClass().getSimpleName()));
    }

    // ---- 笛卡尔积 ----

    /**
     * 计算笛卡尔积：给定轴名列表和对应的取值列表，返回所有参数组合?
     */
    static List<Map<String, Object>> cartesian(List<String> names, List<List<Object>> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(new LinkedHashMap<>());
        for (int d = 0; d < names.size(); d++) {
            List<Map<String, Object>> next = new ArrayList<>();
            for (Map<String, Object> partial : result) {
                for (Object v : values.get(d)) {
                    Map<String, Object> copy = new LinkedHashMap<>(partial);
                    copy.put(names.get(d), v);
                    next.add(copy);
                }
            }
            result = next;
        }
        return result;
    }

    /**
     * 将笛卡尔积参数组合转为可读描述，? "K=Int, V=Long"?
     */
    static String describeCombo(List<String> axisNames, Map<String, Object> combo) {
        StringBuilder sb = new StringBuilder();
        for (String name : axisNames) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(name).append('=').append(combo.get(name));
        }
        return sb.toString();
    }

    // ---- 输出路径解析 ----

    /** 解析输出路径中的 ${...} 表达式? */
    static String resolveOutputPath(String outputPath, Map<String, Object> params) {
        if (outputPath == null || !outputPath.contains("${")) {
            return outputPath;
        }
        List<Token> toks = Lexer.lex(outputPath);
        List<Node> nodes = Parser.parse(toks);
        try {
            return TemplateBody.of(nodes).render(Context.of(params, Map.of()));
        } catch (IOException e) {
            throw new CodeGenException("路径表达式渲染失?: " + e.getMessage(), e);
        }
    }

    /**
     *  @Path ʽΪַ·
     * <ul>
     *   <li>String  ʹ {@link #resolveOutputPath} ݾʽ ${} ﷨</li>
     *   <li>Lson ʽ  ȫ params ֵ</li>
     * </ul>
     *
     * @return ǿ·ַ
     * @throws CodeGenException ·ʽȱʧֵΪ
     */
    private String resolvePathExpr(Map<String, Object> params, FunctionRegistry functions) {
        if (pathExpr == null) {
            throw new CodeGenException("@Path ȱʧ");
        }
        String result;
        if (pathExpr instanceof String s) {
            result = resolveOutputPath(s, params);
        } else {
            Object evaled = RefResolver.eval(pathExpr, params, functions);
            result = evaled == null ? "" : evaled.toString();
        }
        if (result.isEmpty()) {
            throw new CodeGenException("@Path ֵΪ");
        }
        return result;
    }

    /**
     * 求? @SkipWhen 条件表达式。无 skipExpr 或求值为 false/null 时返? false（不跳过）?
     */
    private boolean evalSkip(Map<String, Object> params, FunctionRegistry functions) {
        if (skipExpr == null) return false;
        Object result = RefResolver.eval(skipExpr, params, functions);
        return TemplateUtils.truthy(result);
    }

}
