package com.flora.ramet.engine.ast;

import com.flora.ramet.engine.model.Lson;
import com.flora.ramet.engine.TemplateUtils;
import com.flora.ramet.engine.runtime.Context;
import com.flora.ramet.engine.runtime.RefResolver;

import java.io.IOException;

/**
 * 变量插值节点：模板中 ${表达式} 语法对应的 AST 节点。
 *
 * <p>持有表达式原文 {@link #expr}，render 时先通过 {@link Lson#parse} 解析为
 * Lson 对象，再经 {@link RefResolver#evalCtx} 求值，最后将结果字符串追加到输出缓冲。
 * 若求值结果为 null 则输出空字符串。前导换行由词法阶段的 {@code NEW_LINE} token 承载。
 *
 * <h2>语法示例</h2>
 * <pre>
 * ${user.name}   属性访问
 * ${capitalize(name)}   函数调用
 * ${range(1, 3)}   范围
 * </pre>
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
        if (v == null) {
            if (ctx.strictNull) {
                throw TemplateUtils.err(line, "插值结果为 null（strictNull）：表达式 " + expr
                        + " 未解析出值，请检查 @Param 或改用 @Config{ strictNull: false } 容错");
            }
            return;
        }
        out.append(v.toString());
    }
}
