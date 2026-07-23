package com.flora.codegen.engine.ast;

import com.flora.codegen.engine.TemplateUtils;
import com.flora.codegen.engine.runtime.Context;
import com.flora.codegen.engine.runtime.RefResolver;

import java.io.IOException;
import java.util.List;

/**
 * 循环节点：模板中 {@code <#for var:expr>...<#else>...</#for>} 语法对应的 AST 节点。
 * {@code <#for var:expr>...<#else>...</#for>} 语法对应的 AST 节点。
 *
 * <p>支持 {@code <#continue[depth][:cond]>} 和 {@code <#break[depth][:cond]>}。
 */
public class ForNode extends Node {
    String var;
    Object iterLson;
    List<Node> body;
    List<Node> elseB;

    public ForNode(String var, Object iterLson, List<Node> body, List<Node> elseB) {
        this.var = var;
        this.iterLson = iterLson;
        this.body = body;
        this.elseB = elseB;
    }

    @Override
    public void render(Context ctx, StringBuilder out) throws IOException {
        List<Object> list = TemplateUtils.toList(RefResolver.evalCtx(iterLson, ctx));
        if (list.isEmpty()) {
            if (elseB != null) for (Node n : elseB) n.render(ctx, out);
            return;
        }
        loop:
        for (Object e : list) {
            Context cc = ctx.child();
            cc.setVar(var, e);
            try {
                for (Node n : body) n.render(cc, out);
            } catch (ContinueSignal cs) {
                cs.remaining--;
                if (cs.remaining > 0) throw cs;
            } catch (BreakSignal bs) {
                bs.remaining--;
                if (bs.remaining > 0) throw bs;
                break loop;
            }
        }
    }
}
