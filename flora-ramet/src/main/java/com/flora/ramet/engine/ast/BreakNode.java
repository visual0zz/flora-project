package com.flora.ramet.engine.ast;
import com.flora.ramet.engine.runtime.BreakSignal;

import com.flora.ramet.engine.TemplateUtils;
import com.flora.ramet.engine.runtime.Context;
import com.flora.ramet.engine.runtime.RefResolver;

import java.io.IOException;

/**
 * 循环中断节点：模板中 {@code <#break[depth][:cond]>} 语法对应的 AST 节点。
 *
 * <p>render 时抛出 {@link com.flora.ramet.engine.runtime.BreakSignal}，
 * 由最近的循环节点捕获以退出一层或多层循环；可带层数 {@code [depth]} 与条件 {@code [:cond]}。支持以下形式：
 * <ul>
 *   <li>{@code <#break>} - 退出当前循环</li>
 *   <li>{@code <#break 2>} - 退出两层循环</li>
 *   <li>{@code <#break cond>} - 条件成立时退出</li>
 *   <li>{@code <#break 2:cond>} - 条件成立时退出两层</li>
 * </ul>
 *
 * <h2>语法示例</h2>
 * <pre>
 * &lt;#for x:xs&gt;
 * &lt;#if x == 0&gt;&lt;#break&gt;&lt;/#if&gt;${x}
 * &lt;/#for&gt;
 * </pre>
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
