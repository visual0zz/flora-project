package com.flora.codegen.engine.ast;

import com.flora.codegen.engine.runtime.Context;

/**
 * 注释节点：模板中 {@code <#-- ... -->} 注释块的 AST 表示。
 *
 * <p>持有注释正文 {@link #body} 和所在行号 {@link #line}，
 * render 时直接跳过，不产生任何输出。
 */
public class CommentNode extends Node {
    public final String body;
    public final int line;

    public CommentNode(String body, int line) {
        this.body = body;
        this.line = line;
    }

    @Override
    public void render(Context ctx, StringBuilder out) { /* 不输出 */ }
}
