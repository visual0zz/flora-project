package com.flora.ramet.engine.ast;

import com.flora.ramet.engine.runtime.Context;

import java.io.IOException;

/**
 * 语法树节点：模板渲染的核心抽象，也是语法分析的产出。
 *
 * <p>从 Token 列表构建 AST 请使用 {@link com.flora.ramet.engine.parser.Parser#parse}。
 *
 * <p>表达式求值使用 {@link com.flora.ramet.engine.model.Lson} 解析：
 * <ul>
 *   <li>{@link com.flora.ramet.engine.model.Lson#parse} — 普通表达式（${…} 插值、元数据值）</li>
 *   <li>{@link com.flora.ramet.engine.model.Lson#parse(String, int, String)} — 指令表达式（{@code <#if>}、{@code <#for>}），
 *       支持中缀/前缀函数简写和 {@code ..} 范围运算符</li>
 * </ul>
 * 求值通过 {@link com.flora.ramet.engine.runtime.RefResolver#evalCtx} 完成。
 */
public abstract class Node {

    public abstract void render(Context ctx, StringBuilder out) throws IOException;
}
