package com.flora.codegen.engine.ast;

import com.flora.codegen.engine.TemplateUtils;
import com.flora.codegen.engine.runtime.Context;
import com.flora.codegen.engine.runtime.RefResolver;

import java.io.IOException;

/**
 * <#break> 节点——支持层数和条件。
 * <#break>            → 退出当前循环
 * <#break 2>          → 退出两层循环
 * <#break cond>       → 条件成立时退出
 * <#break 2:cond>     → 条件成立时退出两层
 */
public class BreakNode extends Node {
    final int depth;
    final Object condLson; // null 表示无条件

    public BreakNode(int depth, Object condLson) {
        this.depth = depth;
        this.condLson = condLson;
    }

    @Override
    public void render(Context ctx, StringBuilder out) throws IOException {
        if (condLson != null) {
            Object v = RefResolver.evalCtx(condLson, ctx);
            if (!TemplateUtils.truthy(v)) return;
        }
        throw new BreakSignal(depth);
    }
}
