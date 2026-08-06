package com.flora.ramet.engine.ast;
import com.flora.ramet.engine.runtime.ContinueSignal;
import com.flora.ramet.engine.runtime.BreakSignal;

import com.flora.ramet.engine.TemplateUtils;
import com.flora.ramet.engine.runtime.Context;
import com.flora.ramet.engine.runtime.RefResolver;

import java.io.IOException;
import java.util.List;

/**
 * 循环节点：模板中 {@code <#for var:expr>...<#else>...</#for>} 及其带序号形式
 * {@code <#for index,var:expr>...<#else>...</#for>} 对应的 AST 节点。
 *
 * <p>单名形式 {@code <#for var:expr>} 仅把当前元素绑定到 {@code var}；
 * 双名形式 {@code <#for index,var:expr>} 额外把从 0 开始的遍历序号绑定到
 * 自定义的 {@code index} 变量名，可在循环体内通过 {@code ${index}} 访问。
 *
 * <p>支持 {@code <#continue[depth][:cond]>} 和 {@code <#break[depth][:cond]>}。
 */
public class ForNode extends Node {
    String var;
    String index;
    Object iterLson;
    List<Node> body;
    List<Node> elseB;

    public ForNode(String var, String index, Object iterLson, List<Node> body, List<Node> elseB) {
        this.var = var;
        this.index = index;
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
        for (int i = 0; i < list.size(); i++) {
            Object e = list.get(i);
            Context cc = ctx.child();
            if (index != null) cc.setVar(index, (long) i);
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
