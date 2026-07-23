package com.flora.codegen.engine.runtime;

import com.flora.codegen.engine.ast.MacroDefNode;
import com.flora.codegen.engine.ast.Node;

import java.io.IOException;
import java.util.List;

/**
 * Ramet 模板引擎的渲染执行器——持有已解析的 {@link Node} 列表，负责将其渲染为输出字符串。
 *
 * <p>渲染流程：
 * <ol>
 *   <li>顺序调用每个 {@link Node#render(Context, StringBuilder)} 方法</li>
 *   <li>将每个节点的输出追加到字符串缓冲区</li>
 * </ol>
 *
 * <p>使用 {@link #of(List)} 工厂方法从 Node 列表构造实例，然后调用
 * {@link #render(Context)} 执行渲染。
 */
public final class TemplateBody {

    private final List<Node> nodes;

    private TemplateBody(List<Node> nodes) {
        this.nodes = nodes;
    }

    /** 构造一个模板主体。 */
    public static TemplateBody of(List<Node> nodes) {
        return new TemplateBody(nodes);
    }

    public String render(Context ctx) throws IOException {
        // 渲染前预注册所有宏定义，使它们在模板渲染过程全局可见
        for (Node n : nodes) {
            if (n instanceof MacroDefNode m) {
                ctx.putMacro(m.name, m);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) {
            n.render(ctx, sb);
        }
        return sb.toString();
    }
}
