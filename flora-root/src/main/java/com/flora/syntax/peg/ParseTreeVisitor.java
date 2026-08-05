package com.flora.syntax.peg;

/**
 * 语法树通用访问器：按节点 {@link ParseTree#name()} 分派，返回业务类型 {@code T}。
 *
 * <p>替代专用工具（如 ANTLR）的每文法生成 visitor：此处键是 {@code name()} 字符串而非生成的方法名。
 * 默认按先序递归遍历；子类覆写 {@link #visit} 即可做任意投影（构造领域模型、语义分析等）。
 */
public interface ParseTreeVisitor<T> {

    /** 访问单个节点并返回结果。 */
    T visit(ParseTree node);

    /** 先序递归访问子节点，返回 null；适合只想遍历的用例。 */
    default T visitChildren(ParseTree node) {
        for (ParseTree child : node.children()) {
            visit(child);
        }
        return null;
    }
}
