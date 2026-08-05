package com.flora.syntax.peg;

import java.util.List;

/**
 * 语法树节点：引擎内置固定的通用类型（密封结构树），不按文法生成子类。
 *
 * <p>两个分支对应"节点是什么结构位置"的通用分类：{@link RuleNode} 为非终结节点（文法规则或带
 * {@code #Label} 的候选的应用），{@link TokenNode} 为终结叶子（包裹一个 {@link Token}）。具体维度
 * 靠 {@code name()}（规则名 / {@code #Label} / 词法规则名 / 字面量源码）区分，与 {@link TokenKind}
 * 的"通用类别 + {@code typeName} 特指"对称。
 */
public sealed interface ParseTree permits ParseTree.RuleNode, ParseTree.TokenNode {

    /** 导航标识：{@code #Label} 优先，否则文法规则名 / 词法规则名 / 字面量源码。 */
    String name();

    /** 匹配子串（RuleNode 由子节点文本拼接；TokenNode 为 token 原文）。 */
    String text();

    /** 起始字符偏移（0 基，不含）。 */
    int start();

    /** 结束字符偏移（0 基，不含）。 */
    int end();

    List<ParseTree> children();

    boolean isLeaf();

    /** 仅 {@link TokenNode} 持有底层的 {@link Token}；其余节点返回 {@code null}。 */
    default Token token() { return null; }

    /** 非终结节点：一条文法规则（或带 {@code #Label} 的候选）的应用，持有子节点。 */
    record RuleNode(String ruleName, String label, List<ParseTree> children,
                    int start, int end) implements ParseTree {
        @Override
        public String name() { return label != null ? label : ruleName; }

        @Override
        public String text() {
            StringBuilder sb = new StringBuilder();
            for (ParseTree c : children) sb.append(c.text());
            return sb.toString();
        }

        @Override
        public boolean isLeaf() { return false; }
    }

    /** 终结叶子：包裹一个 {@link Token}（词法规则引用 / 字面量叶子）。 */
    record TokenNode(Token token) implements ParseTree {
        @Override
        public String name() { return token.typeName(); }

        @Override
        public String text() { return token.text(); }

        @Override
        public int start() { return token.start(); }

        @Override
        public int end() { return token.end(); }

        @Override
        public List<ParseTree> children() { return List.of(); }

        @Override
        public boolean isLeaf() { return true; }

        @Override
        public Token token() { return token; }
    }
}
