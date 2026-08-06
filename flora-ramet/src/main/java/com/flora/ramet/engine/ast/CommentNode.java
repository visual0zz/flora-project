package com.flora.ramet.engine.ast;

import com.flora.ramet.engine.runtime.Context;

/**
 * 注释节点：模板中 {@code <#-- ... -->} 注释块的 AST 表示。
 *
 * <p>持有注释正文 {@link #body} 和所在行号 {@link #line}，
 * render 时直接跳过，不产生任何输出。
 *
 * <h2>语法示例</h2>
 * <pre>
 * &lt;#-- 这是注释，不会出现在输出中 --&gt;
 * </pre>
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
