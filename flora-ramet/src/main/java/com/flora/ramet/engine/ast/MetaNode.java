package com.flora.ramet.engine.ast;

import com.flora.ramet.engine.parser.MetaParser;
import com.flora.ramet.engine.runtime.Context;

/**
 * 元数据节点：模板开头的 @Param / @Cartesian / @Path / @Config 指令对应的 AST 节点。
 *
 * <p>持有经 {@link com.flora.ramet.engine.parser.MetaParser} 解析后的
 * {@link com.flora.ramet.engine.parser.MetaParser.MetaData}，
 * 可通过 {@link #data()} 获取。render 时不产生输出，仅在编译阶段收集元信息。
 *
 * <h2>语法示例</h2>
 * <pre>
 * &lt;#meta&gt;
 * &#64;Param{ name: "A" }
 * &#64;Cartesian{ K: ["X", "Y"] }
 * &#64;Path{ "Out.java" }
 * &lt;/#meta&gt;
 * </pre>
 */
public class MetaNode extends Node {
    MetaParser.MetaData data;

    public MetaParser.MetaData data() { return data; }

    public MetaNode(MetaParser.MetaData data) {
        this.data = data;
    }

    @Override
    public void render(Context ctx, StringBuilder out) { /* 不输出 */ }
}
