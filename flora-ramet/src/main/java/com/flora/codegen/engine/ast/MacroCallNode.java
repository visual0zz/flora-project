package com.flora.codegen.engine.ast;

import com.flora.codegen.engine.CodeGenException;
import com.flora.codegen.engine.TemplateUtils;
import com.flora.codegen.engine.runtime.Context;
import com.flora.codegen.engine.runtime.RefResolver;

import java.io.IOException;
import java.util.List;

/**
 * 宏调用节点：模板中 {@code <@macroName args/>} 语法对应的 AST 节点。
 *
 * <p>持有宏名 {@link #name}、实参表达式列表 {@link #lsonArgs} 和所在行号 {@link #line}。
 * render 时从上下文查找已定义的宏，创建子上下文并绑定实参后递归渲染宏体。
 * 若宏未定义则抛出异常。
 */
public class MacroCallNode extends Node {
    String name;
    List<Object> lsonArgs;
    int line;

    public MacroCallNode(String name, List<Object> lsonArgs, int line) {
        this.name = name;
        this.lsonArgs = lsonArgs;
        this.line = line;
    }

    @Override
    public void render(Context ctx, StringBuilder out) throws IOException {
        MacroDefNode m = ctx.getMacro(name);
        if (m == null) throw TemplateUtils.err(line, "未定义的宏: " + name);
        Context cc = ctx.child();
        // 构建宏调用栈条目并压栈
        String tail = ctx.source != null ? ctx.source : "?";
        String callEntry = name + " @ " + tail + ":" + line;
        ctx.pushMacroCall(callEntry);
        try {
            for (int k = 0; k < m.params.size(); k++) {
                MacroDefNode.MacroParam mp = m.params.get(k);
                Object a;
                if (k < lsonArgs.size()) {
                    a = RefResolver.evalCtx(lsonArgs.get(k), ctx);
                } else if (mp.defaultValue() != null) {
                    a = RefResolver.evalCtx(mp.defaultValue(), ctx);
                } else {
                    a = null;
                }
                cc.setVar(mp.name(), a);
            }
            for (Node n : m.body) n.render(cc, out);
        } catch (CodeGenException e) {
            String msg = TemplateUtils.appendChain(e.getMessage(), ctx);
            throw new CodeGenException(msg, e);
        } finally {
            ctx.popMacroCall();
        }
    }
}
