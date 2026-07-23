package com.flora.codegen.engine.ast;

import com.flora.codegen.engine.TemplateUtils;
import com.flora.codegen.engine.runtime.Context;
import com.flora.codegen.engine.runtime.RefResolver;

import java.io.IOException;
import java.util.List;

/**
 * 条件分支节点：模板中 {@code <#if cond>...<#else>...</#if>} 语法对应的 AST 节点。
 *
 * <p>持有预解析的条件表达式 {@link #condLson}、真分支子节点列表 {@link #thenB}
 * 和可选的假分支子节点列表 {@link #elseB}。render 时先对条件求值，
 * 若为真值则渲染 thenB，否则渲染 elseB（若存在）。
 */
public class IfNode extends Node {
    Object condLson;
    List<Node> thenB;
    List<Node> elseB;

    public IfNode(Object condLson, List<Node> thenB, List<Node> elseB) {
        this.condLson = condLson;
        this.thenB = thenB;
        this.elseB = elseB;
    }

    @Override
    public void render(Context ctx, StringBuilder out) throws IOException {
        if (TemplateUtils.truthy(RefResolver.evalCtx(condLson, ctx))) {
            for (Node n : thenB) n.render(ctx, out);
        } else if (elseB != null) {
            for (Node n : elseB) n.render(ctx, out);
        }
    }
}
