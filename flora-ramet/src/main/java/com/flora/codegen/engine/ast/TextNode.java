package com.flora.codegen.engine.ast;

import com.flora.codegen.engine.runtime.Context;

/**
 * 文本节点：模板中不包含模板语法的普通文本片段。
 *
 * <p>render 时将 {@link #text} 原样追加到输出缓冲区，不做任何转义或求值。
 */
public class TextNode extends Node {
    String text;
    boolean leadingNewline;

    public TextNode(String text, boolean leadingNewline) {
        this.text = text;
        this.leadingNewline = leadingNewline;
    }

    @Override
    public void render(Context ctx, StringBuilder out) {
        if (leadingNewline) out.append('\n');
        out.append(text);
    }
}
