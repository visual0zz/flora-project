package com.flora.codegen.engine.ast;

import com.flora.codegen.CompiledTemplate;
import com.flora.codegen.engine.CodeGenException;
import com.flora.codegen.engine.TemplateUtils;
import com.flora.codegen.engine.runtime.Context;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 模板包含节点：模板中 {@code <#include "path">} 语法对应的 AST 节点。
 *
 * <p>持有路径表达式 {@link #pathLson}。render 时先对路径求值，随后按如下规则解析：
 * <ul>
 *   <li>默认以<b>发起 include 的文件所在文件夹</b>为基准做相对解析
 *       （基准目录取自 {@link Context#source} 的父目录）；</li>
 *   <li>路径以 {@code '/'} 开头时，视为<b>操作系统绝对路径</b>，
 *       relativize 为相对 {@link Context#templatesRoot} 的 key 后查找。</li>
 * </ul>
 * 解析出的 key 用于在 {@link Context#includes} 中查找已编译模板，随后创建子上下文
 * （其 {@code source} 更新为该目标模板的相对路径）递归渲染。包含循环检测通过
 * {@link Context#includeChain} 实现。
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
        Object p = com.flora.codegen.engine.runtime.RefResolver.evalCtx(pathLson, ctx);
        if (!(p instanceof String s))
            throw TemplateUtils.err(line, null, "#include 路径必须求值为字符串", ctx);

        // 解析 include 路径：
        //  - 以 '/' 开头 → 操作系统绝对路径，relativize 为相对 templatesRoot 的 key
        //  - 否则        → 相对发起 include 的文件所在文件夹（ctx.source 的父目录）
        String key;
        CompiledTemplate compiled;
        if (s.startsWith("/")) {
            if (ctx.templatesRoot != null) {
                Path abs = Paths.get(s).normalize();
                Path root = Paths.get(ctx.templatesRoot).normalize();
                try {
                    key = root.relativize(abs).toString().replace('\\', '/');
                } catch (IllegalArgumentException e) {
                    throw TemplateUtils.err(line, null, "#include 路径不在模板根目录下: " + s, ctx);
                }
            } else {
                // 无 templatesRoot 时回退为旧行为：去前导 / 后直接作为 key
                key = s.substring(1);
            }
            compiled = ctx.includes.get(key);
        } else {
            String base = "";
            if (ctx.source != null) {
                Path parent = Paths.get(ctx.source).getParent();
                if (parent != null) base = parent.toString();
            }
            Path resolved = Paths.get(base).resolve(s).normalize();
            key = resolved.toString().replace('\\', '/');
            compiled = ctx.includes.get(key);
        }

        if (compiled == null) {
            throw TemplateUtils.err(line, null, "#include 未找到模板: " + s, ctx);
        }
        if (!ctx.addIncludeChain(s)) {
            throw TemplateUtils.err(line, null, "#include 循环依赖: " + s, ctx);
        }
        try {
            // 被 include 模板的 source 更新为其相对路径 key，使其内部的 include
            // 继续以「该文件所在文件夹」为基准解析。
            Context ic = ctx.child(key);
            for (Node n : compiled.nodes()) {
                n.render(ic, out);
            }
        } catch (CodeGenException e) {
            String msg = TemplateUtils.appendChain(e.getMessage(), ctx);
            throw new CodeGenException(msg, e);
        } finally {
            ctx.removeIncludeChain(s);
        }
    }
}
