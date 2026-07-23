package com.flora.codegen.engine.parser;

import com.flora.codegen.engine.CodeGenException;
import com.flora.codegen.engine.TemplateUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模板头部元数据解析器：模板元数据的唯一所有者。
 *
 * <p>负责把 {@code <#meta>...</#meta>} 块中声明中
 * {@code @Cartesian{...}} / {@code @Path{...}} 文本解析为结构化数据。所有与「元数据格式」
 * 相关的逻辑（词法切分、花括号配对、JSON 解析）都集中在本类。
 *
 * <p>约束：
 * <ul>
 *   <li>一个 meta 块中每种类型（@Param / @Cartesian / @Path）最多出现一次</li>
 *   <li>单个 {@code @Param{...}} 内部也不允许重复 key</li>
 * </ul>
 */
public final class MetaParser {

    private MetaParser() {
    }

/**
 * 一次元数据解析的结果。
 *
 * @param param      {@code @Param{...}} 解析后的 JSON object（无则 null）
 * @param cartesian {@code @Cartesian{...}} 解析后的 JSON object（键为轴名，值为数组，无则 null）
 * @param pathValue  {@code @Path{...}} 内部的原始解析结果（String/Reference/...，无则 null）
 * @param config     {@code @Config{...}} 解析后的 JSON object（无则 null）
 * @param skipWhen   {@code @SkipWhen{...}} 跳过条件表达式（Lson 解析后的 AST，无则 null）
 */
public record MetaData(
        Map<String, Object> param,
        Map<String, Object> cartesian,
        Object pathValue,
        Map<String, Object> config,
        Object skipWhen
) {}

    /**
     * 解析一个 meta 注释块的正文，返回其中所有块类型的结果。
     *
     * @param body meta 注释块去掉 {@code <#-- -->} 后的正文
     * @param line 该 meta 块所在行号，用于错误定位
     * @return 解析后的元数据；即使块中没有任何声明也返回非 null 结果
     */
    public static MetaData parse(String body, int line, String source) {
        List<MetaToken> mtoks = tokenize(body, line);
        Map<String, Object> param = parseBlock(mtoks, MetaTokenType.PARAM, line, source);
        Map<String, Object> cartesian = parseBlock(mtoks, MetaTokenType.CARTESIAN, line, source);
        Object pathValue = parsePathValue(mtoks, line, source);
        Map<String, Object> config = parseBlock(mtoks, MetaTokenType.CONFIG, line, source);
        Object skipWhen = parseSkipWhenValue(mtoks, line, source);
        return new MetaData(param, cartesian, pathValue, config, skipWhen);
    }

    public static MetaData parse(String body, int line) {
        return parse(body, line, null);
    }

    // ---- 词法切分：识别 @Param{...} / @Cartesian{...} / @Path{...} ----

    private enum MetaTokenType { PARAM, CARTESIAN, PATH, CONFIG, SKIP_WHEN }

    private record MetaToken(MetaTokenType type, String body) {}

    private static final String[] BLOCK_PREFIXES = {
            "@Param{", "@Cartesian{", "@Path{", "@Config{", "@SkipWhen{"
    };
    private static final MetaTokenType[] BLOCK_TYPES = {
            MetaTokenType.PARAM,
            MetaTokenType.CARTESIAN, MetaTokenType.PATH, MetaTokenType.CONFIG,
            MetaTokenType.SKIP_WHEN
    };

    private static List<MetaToken> tokenize(String body, int line) {
        List<MetaToken> result = new ArrayList<>();
        int i = 0;
        int len = body.length();
        while (i < len) {
            boolean matched = false;
            for (int p = 0; p < BLOCK_PREFIXES.length; p++) {
                if (body.startsWith(BLOCK_PREFIXES[p], i)) {
                    int start = i + BLOCK_PREFIXES[p].length();
                    int end = findClosingBrace(body, start, line);
                    result.add(new MetaToken(BLOCK_TYPES[p], body.substring(start, end)));
                    i = end + 1;
                    matched = true;
                    break;
                }
            }
            if (!matched) i++;
        }
        // 校验：同类型只能出现一次
        Set<MetaTokenType> seen = new HashSet<>();
        for (MetaToken mt : result) {
            if (!seen.add(mt.type())) {
                throw new CodeGenException(
                        "模板元数据中 @" + mt.type() + " 重复定义（每类最多一个）");
            }
        }
        return result;
    }

    private static int findClosingBrace(String body, int start, int line) {
        int depth = 1;
        int i = start;
        boolean inStr = false;
        while (i < body.length() && depth > 0) {
            char c = body.charAt(i);
            if (inStr) {
                if (c == '\\') {
                    i += 2; // 跳过 \" \\ 等转义序列，避免 \" 中的 " 错误结束字符串
                    continue;
                }
                if (c == '"') inStr = false;
            } else {
                if (c == '"') inStr = true;
                else if (c == '{') depth++;
                else if (c == '}' && --depth == 0) return i;
            }
            i++;
        }
        throw TemplateUtils.err(line, "模板元数据缺少闭合 }");
    }

    // ---- 语义解析：每个块的内容 → Object ----

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseBlock(List<MetaToken> mtoks, MetaTokenType type, int line, String source) {
        for (MetaToken mt : mtoks) {
            if (mt.type() == type) {
                try {
                    return (Map<String, Object>) Lson.parse("{" + mt.body() + "}", line);
                } catch (CodeGenException e) {
                    throw e;  // 保留原始错误（如重复 key）
                } catch (Exception e) {
                    throw new CodeGenException("模板元数据 @" + type + " 解析失败: " + e.getMessage());
                }
            }
        }
        return null;
    }

    /** @Path 的内容使用表达式解析（支持函数调用、运算符和引用）。 */
    private static Object parsePathValue(List<MetaToken> mtoks, int line, String source) {
        for (MetaToken mt : mtoks) {
            if (mt.type() == MetaTokenType.PATH) {
                try {
                    return Lson.parse(mt.body(), line);
                } catch (Exception e) {
                    throw new CodeGenException("模板元数据 @Path 解析失败: " + e.getMessage());
                }
            }
        }
        return null;
    }

    /** @SkipWhen 的内容使用表达式解析（布尔条件，为 true 时跳过本次生成）。 */
    private static Object parseSkipWhenValue(List<MetaToken> mtoks, int line, String source) {
        for (MetaToken mt : mtoks) {
            if (mt.type() == MetaTokenType.SKIP_WHEN) {
                try {
                    return Lson.parse(mt.body(), line);
                } catch (Exception e) {
                    throw new CodeGenException("模板元数据 @SkipWhen 解析失败: " + e.getMessage());
                }
            }
        }
        return null;
    }
}
