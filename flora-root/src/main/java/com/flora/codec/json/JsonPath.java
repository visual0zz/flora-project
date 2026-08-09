package com.flora.codec.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RFC 9535 JSONPath 表达式引擎。
 * <p>支持完整语法：{@code .key}、{@code ["key"]}、{@code [0]}、{@code [-1]}、
 * {@code [0,1,2]}、{@code [0:5:2]}、{@code [*]}、{@code ..key}、{@code ..*}、
 * {@code [?(expr)]}（含比较/逻辑/函数 {@code length()} / {@code count()}）。</p>
 */
public final class JsonPath {

    private JsonPath() {}

    /**
     * 执行 JSONPath 表达式求值（向后兼容）。
     * <p>查询结果有 0 个元素时返回 {@code null}，1 个时返回该元素，多个时返回 {@code List}。
     * 传入 {@link JsonValue} 时会先展开为原生树再求值。</p>
     *
     * @param root 根对象（可为 {@link JsonValue} 或原生 {@code Map}/{@code List}）
     * @param path JSONPath 表达式
     * @return 查询结果，或 {@code null}
     */
    public static Object eval(Object root, String path) {
        List<Object> results = evalAll(root, path);
        if (results.isEmpty()) return null;
        if (results.size() == 1) return results.get(0);
        return results;
    }

    /**
     * 执行 JSONPath 表达式求值，始终返回节点值列表。
     *
     * @param root 根对象（可为 {@link JsonValue} 或原生 {@code Map}/{@code List}）
     * @param path JSONPath 表达式
     * @return 匹配的节点值列表（永不 null）
     */
    public static List<Object> evalAll(Object root, String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("JSONPath 表达式不能为空");
        }
        String expr = path.trim();
        if (!expr.startsWith("$")) {
            throw new IllegalArgumentException("JSONPath 表达式必须以 '$' 开头");
        }

        Object nativeRoot = (root instanceof JsonValue) ? ((JsonValue) root).toNative() : root;
        List<Token> tokens = JsonPathTokenizer.tokenize(expr);
        List<Selector> selectors = JsonPathParser.parse(tokens);
        return JsonPathEvaluator.evaluate(nativeRoot, selectors);
    }
}
