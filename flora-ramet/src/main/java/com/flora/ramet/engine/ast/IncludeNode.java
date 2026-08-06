package com.flora.ramet.engine.ast;
import com.flora.ramet.engine.runtime.RefResolver;

import com.flora.ramet.engine.CodeGenException;
import com.flora.ramet.engine.Template;
import com.flora.ramet.engine.TemplateUtils;
import com.flora.ramet.engine.runtime.Context;

import java.io.IOException;

/**
 * 模板包含节点：模板中 {@code <#include "path">} 语法对应的 AST 节点。
 *
 * <p>持有路径表达式 {@link #pathLson}。render 时先对路径求值，再交给
 * {@link Context#repo} 把路径解析为 key 并加载对应的 {@link Template}，
 * 随后创建子上下文（其 {@code source} 更新为目标模板的 key）递归渲染。
 * 包含循环检测通过 {@link Context#includeChain} 实现。</p>
 *
 * <h2>语法示例</h2>
 * <pre>
 * &lt;#include "header.ftl"/&gt;
 * &lt;#include "/abs/path.ftl"/&gt;
 * </pre>
 */
public class IncludeNode extends Node {
    Object pathLson;
    int line;

    public IncludeNode(Object pathLson, int line) {
        this.pathLson = pathLson;
        this.line = line;
    }

    @Override
    public void render(Context ctx, StringBuilder out) throws IOException {
        Object p = RefResolver.evalCtx(pathLson, ctx);
        if (!(p instanceof String s))
            throw TemplateUtils.err(line, null, "#include 路径必须求值为字符串", ctx);

        // 路径解析与模板加载统一交给仓库：相对路径以发起 include 的文件目录为基准，
        // 以 '/' 开头的路径以仓库根为基准（均由 TemplateRepository 实现决定）。
        String key = ctx.repo.resolve(ctx.source, s);
        Template compiled = ctx.repo.load(key);

        if (!ctx.addIncludeChain(key)) {
            throw TemplateUtils.err(line, null, "#include 循环依赖: " + s, ctx);
        }
        try {
            // 被 include 模板的 source 更新为其 key，使其内部的 include 继续以正确基准解析。
            Context ic = ctx.child(key);
            for (Node n : compiled.nodes()) {
                n.render(ic, out);
            }
        } catch (CodeGenException e) {
            String msg = TemplateUtils.appendChain(e.getMessage(), ctx);
            throw new CodeGenException(msg, e);
        } finally {
            ctx.removeIncludeChain(key);
        }
    }
}
