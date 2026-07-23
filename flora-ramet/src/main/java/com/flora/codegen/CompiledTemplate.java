package com.flora.codegen;

import com.flora.codegen.engine.ast.Node;

import java.util.List;

/**
 * 预编译模板——保存一次解析管线（Lexer → Parser）的输出结果。
 *
 * <p>用于 include 缓存场景：当模板 A 通过 {@code <#include "sub.ftl">} 引用模板 B 时，
 * 无需每次都重新执行完整的词法/语法解析管线，直接使用已缓存的
 * {@link CompiledTemplate} 即可加速渲染。
 *
 * <p>仅包含 AST 节点列表，不包含元数据。元数据（{@code @Param}、{@code @Path} 等）
 * 仅在入口模板通过 {@link CodeGenUtil#generate} 解析。
 *
 * @param nodes 已解析的 AST 节点列表
 */
public record CompiledTemplate(List<Node> nodes) {

    public CompiledTemplate {
        if (nodes == null) throw new IllegalArgumentException("nodes must not be null");
    }
}
