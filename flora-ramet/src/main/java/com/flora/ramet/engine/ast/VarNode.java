package com.flora.ramet.engine.ast;

import com.flora.ramet.engine.model.Lson;
import com.flora.ramet.engine.runtime.Context;
import com.flora.ramet.engine.runtime.RefResolver;

import java.io.IOException;

/**
 * 变量插值节点：模板中 ${表达式} 语法对应的 AST 节点。
 *
 * <p>持有表达式原文 {@link #expr}，render 时先通过 {@link Lson#parse} 解析为
 * Lson 对象，再经 {@link RefResolver#evalCtx} 求值，最后将结果字符串追加到输出缓冲。
 * 若求值结果为 null 则输出空字符串。前导换行由词法阶段的 {@code NEW_LINE} token 承载。
 */
public class VarNode extends Node {
    String expr;
    int line;

    public VarNode(String text, int line) {
        this.expr = text.trim();
        this.line = line;
    }

    @Override
    public void render(Context ctx, StringBuilder out) throws IOException {
        Object lsonVal = Lson.parse(expr, line);
        Object v = RefResolver.evalCtx(lsonVal, ctx);
        out.append(v == null ? "" : v.toString());
    }
}
