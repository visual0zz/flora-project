package com.flora.codegen.engine.ast;

import com.flora.codegen.engine.runtime.Context;

import java.util.List;
import java.util.Objects;

/**
 * 宏定义节点：模板中 {@code <#macro name:param1,param2=default,...>} 语法对应的 AST 节点。
 *
 * <p>持有宏名 {@link #name}、参数列表 {@link #params}（含可选默认值）和宏体子节点列表 {@link #body}。
 * render 时将自身注册到当前上下文的宏表中，供后续 {@link MacroCallNode} 调用，不产生输出。
 */
public class MacroDefNode extends Node {
    /** 宏参数：参数名 + 可选的默认值表达式（Lson AST，为 null 表示无默认值）。 */
    public record MacroParam(String name, Object defaultValue) {
        public MacroParam {
            Objects.requireNonNull(name);
        }
    }

    public final String name;
    public final List<MacroParam> params;
    public final List<Node> body;

    public MacroDefNode(String name, List<MacroParam> params, List<Node> body) {
        this.name = name;
        this.params = params;
        this.body = body;
    }

    @Override
    public void render(Context ctx, StringBuilder out) {
        // 宏定义不产生输出；注册工作由 TemplateBody 在渲染前统一完成
    }
}
