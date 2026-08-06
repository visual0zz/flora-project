package com.flora.ramet.engine.ast;
import com.flora.ramet.engine.TemplateUtils;
import com.flora.ramet.engine.runtime.ContinueSignal;

import com.flora.ramet.engine.runtime.Context;
import com.flora.ramet.engine.runtime.RefResolver;

import java.io.IOException;

/**
 * 循环跳过节点：模板中 {@code <#continue[depth][:cond]>} 语法对应的 AST 节点。
 *
 * <p>render 时抛出 {@link com.flora.ramet.engine.runtime.ContinueSignal}，
 * 由最近的循环节点捕获以跳过一层或多层循环的当前迭代；可带层数 {@code [depth]} 与条件 {@code [:cond]}。支持以下形式：
 * <ul>
 *   <li>{@code <#continue>} - 跳过当前迭代</li>
 *   <li>{@code <#continue 2>} - 跳过两层迭代</li>
 *   <li>{@code <#continue cond>} - 条件成立时跳过</li>
 *   <li>{@code <#continue 2:cond>} - 条件成立时跳过两层</li>
 * </ul>
 *
 * <h2>语法示例</h2>
 * <pre>
 * &lt;#for x:xs&gt;
 * &lt;#if x == 0&gt;&lt;#continue&gt;&lt;/#if&gt;${x}
 * &lt;/#for&gt;
 * </pre>
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
            if (!com.flora.ramet.engine.TemplateUtils.truthy(v)) return;
        }
        throw new ContinueSignal(depth);
    }
}
