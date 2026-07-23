package com.flora.codegen.engine.runtime;

import com.flora.codegen.LazyArg;
import com.flora.codegen.TemplateFunction;
import com.flora.codegen.engine.CodeGenException;
import com.flora.codegen.engine.TemplateUtils;
import com.flora.codegen.engine.parser.Lson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 引用解析器：单值命名空间中的 Lson 表达式求值。
 *
 * <p>两类使用场景：
 * <ul>
 *   <li>{@link #resolve} + {@link #resolveValue} — 模板元数据的全量引用解析（含成环检测）</li>
 *   <li>{@link #eval} / {@link #evalCtx} — 单表达式的求值（已解析的上下文或运行时 Context）</li>
 * </ul>
 *
 * <p>支持的节点类型：
 * {@link Lson.Reference}、{@link Lson.FunctionCall}、
 * {@link Lson.PropertyAccess}、{@code Map}、{@code List}、基本类型 / String / null。
 *
 * <p>所有运算符（greaterThan、lessThan、and、not 等）按函数调用处理，通过 {@link FunctionRegistry} 分派。
 *
 * <p>{@link #eval} 和 {@link #evalCtx} 共享同一套求值逻辑，
 * 区别仅在于变量解析方式：前者从已解析的 {@code Map} 中查找，后者从运行时 {@link Context} 链中查找。
 */
public final class RefResolver {

    private RefResolver() {
    }

    /**
     * 解析命名空间中所有值的引用、函数调用和属性链。
     */
    public static Map<String, Object> resolve(Map<String, Object> rawNs,
                                               FunctionRegistry functions) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();

        for (String key : rawNs.keySet()) {
            resolved.put(key,
                    resolveValue(rawNs, rawNs.get(key), visiting, visited, functions));
        }
        return resolved;
    }

    /**
     * 在已解析的上下文中求值单个 Lson 表达式。
     * resolvedCtx 中的值必须是纯 Java 值（非 Lson AST）。
     */
    public static Object eval(Object expr, Map<String, Object> resolvedCtx,
                               FunctionRegistry functions) {
        return evalCommon(expr, name -> {
            Object v = resolvedCtx.get(name);
            if (v == null) {
                throw new CodeGenException("未找到引用目标: " + name);
            }
            return v;
        }, functions);
    }

    /**
     * 在运行时 Context 中求值单个 Lson 表达式。
     * 变量通过 {@link Context#lookup} 查找（同时支持 params 和局部变量）。
     */
    public static Object evalCtx(Object expr, Context ctx) {
        return evalCommon(expr, ctx::lookup, ctx.functions);
    }

    // ---- 共享求值核心 ----

    /**
     * 共享的表达式求值逻辑。
     *
     * <p>{@code varResolver} 负责将名称解析为值，不同调用方提供不同的实现：
     * <ul>
     *   <li>{@link #eval} → 从 {@code Map<String, Object>} 直接取值</li>
     *   <li>{@link #evalCtx} → 通过 {@link Context#lookup} 在上下文链中查找</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private static Object evalCommon(Object expr, Function<String, Object> varResolver,
                                      FunctionRegistry functions) {
        if (expr instanceof Lson.FunctionCall(String name, List<Object> args)) {
            TemplateFunction fn = functions.get(name);
            if (fn == null) {
                throw new CodeGenException("未找到函数: " + name);
            }
            // 包裹为惰性参数，由函数决定何时求值（支持短路）
            if (fn.arity() >= 0 && args.size() != fn.arity()) {
                throw new CodeGenException(
                        "函数 " + name + " 期望 " + fn.arity()
                                + " 个参数，实际传入 " + args.size() + " 个");
            }
            List<LazyArg> lazyArgs = new ArrayList<>(args.size());
            for (Object arg : args) {
                Object captured = arg;
                lazyArgs.add(() -> evalCommon(captured, varResolver, functions));
            }
            return fn.apply(lazyArgs);
        }
        if (expr instanceof Lson.PropertyAccess(Object target1, String property)) {
            Object target = evalCommon(target1, varResolver, functions);
            return TemplateUtils.getProperty(target, property);
        }
        if (expr instanceof Lson.IndexAccess(Object target1, Object index1)) {
            Object target = evalCommon(target1, varResolver, functions);
            Object idx = evalCommon(index1, varResolver, functions);
            return TemplateUtils.getIndex(target, idx);
        }
        if (expr instanceof Lson.Reference(String name)) {
            Object target = varResolver.apply(name);
            return evalCommon(target, varResolver, functions);
        }
        if (expr instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                result.put((String) e.getKey(),
                        evalCommon(e.getValue(), varResolver, functions));
            }
            return result;
        }
        if (expr instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(evalCommon(item, varResolver, functions));
            }
            return result;
        }
        return expr;
    }

    // ---- 带成环检测的递归解析（用于元数据命名空间） ----

    /**
     * 解析元数据命名空间中的单个值。
     *
     * <p>Reference 分支使用 {@code visiting}/{@code visited} 集合进行成环检测，
     * 其余分支委托给 {@link #evalCommon}（通过携带成环检测的 {@code varResolver}），
     * 消除 {@code evalCommon} 中 5 个分支的手动重复。
     */
    @SuppressWarnings("unchecked")
    private static Object resolveValue(Map<String, Object> ns, Object value,
                                        Set<String> visiting,
                                        Set<String> visited,
                                        FunctionRegistry functions) {
        if (value instanceof Lson.Reference(String name)) {
            if (visiting.contains(name)) {
                throw new CodeGenException("引用成环: " + name);
            }
            Object target = ns.get(name);
            if (target == null) {
                throw new CodeGenException("未找到引用目标: " + name);
            }
            if (visited.contains(name)) {
                return target;
            }
            visiting.add(name);
            Object resolved = resolveValue(ns, target,
                    visiting, visited, functions);
            visiting.remove(name);
            visited.add(name);
            return resolved;
        }
        // 非 Reference 类型委托给 evalCommon（varResolver 内嵌成环检测）
        return evalCommon(value, name -> {
            if (visiting.contains(name)) {
                throw new CodeGenException("引用成环: " + name);
            }
            Object target = ns.get(name);
            if (target == null) {
                throw new CodeGenException("未找到引用目标: " + name);
            }
            if (visited.contains(name)) {
                return target;
            }
            visiting.add(name);
            Object resolved = resolveValue(ns, target, visiting, visited, functions);
            visiting.remove(name);
            visited.add(name);
            return resolved;
        }, functions);
    }
}
