package com.flora.ramet.engine.ast;

import com.flora.ramet.engine.runtime.Context;

import java.io.IOException;

/**
 * 语法树节点：模板渲染的核心抽象，也是语法分析的产出。
 *
 * <p>从 Token 列表构建 AST 请使用 {@link com.flora.ramet.engine.parser.Parser#parse}。
 * 表达式求值使用 {@link com.flora.ramet.engine.model.Lson} 解析，再经
 * {@link com.flora.ramet.engine.runtime.RefResolver#evalCtx} 完成；普通表达式与指令表达式
 * 分别支持属性访问、函数调用与中缀/前缀运算符（如 {@code greaterThan}、{@code ..} 范围）。
 *
 * <h2>语法示例</h2>
 * <pre>
 * &lt;#meta&gt;
 * &#64;Param{ name: "World", items: ["a", "b"] }
 * &#64;Path{ "Out.java" }
 * &lt;/#meta&gt;
 * &lt;#-- 注释不输出 --&gt;
 * &lt;#macro greet:who&gt;Hi ${who}&lt;/#macro&gt;
 * &lt;@greet "Bob"/&gt;
 * &lt;#for i,it:items&gt;
 * ${i}:${it}&lt;#if i greaterThan 0&gt;,&lt;/#if&gt;
 * &lt;/#for&gt;
 * &lt;#include "part.ftl"/&gt;
 * </pre>
 */
public abstract class Node {

    public abstract void render(Context ctx, StringBuilder out) throws IOException;
}
