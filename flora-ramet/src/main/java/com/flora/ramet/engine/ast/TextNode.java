package com.flora.ramet.engine.ast;

import com.flora.ramet.engine.runtime.Context;

/**
 * 文本节点：模板中不包含模板语法的普通文本片段。
 *
 * <p>render 时将 {@link #text} 原样追加到输出缓冲区，不做任何转义或求值。
 * 前导换行由词法阶段的 {@code NEW_LINE} token 承载，此处无需单独处理。
 */
public class TextNode extends Node {
    String text;

    public TextNode(String text) {
        this.text = text;
    }

    @Override
    public void render(Context ctx, StringBuilder out) {
        out.append(text);
    }
}
