package com.flora.codegen.engine.ast;

import com.flora.codegen.engine.runtime.Context;
import com.flora.codegen.engine.runtime.RefResolver;

import java.io.IOException;

/**
 * <#continue> 节点——支持层数和条件。
 * <#continue>            → 跳过当前迭代
 * <#continue 2>          → 跳过两层迭代
 * <#continue cond>       → 条件成立时跳过
 * <#continue 2:cond>     → 条件成立时跳过两层
 */
public class ContinueNode extends Node {
    final int depth;
    final Object condLson; // null 表示无条件

    public ContinueNode(int depth, Object condLson) {
        this.depth = depth;
        this.condLson = condLson;
    }

    @Override
    public void render(Context ctx, StringBuilder out) throws IOException {
        if (condLson != null) {
            Object v = RefResolver.evalCtx(condLson, ctx);
            if (!com.flora.codegen.engine.TemplateUtils.truthy(v)) return;
        }
        throw new ContinueSignal(depth);
    }
}
