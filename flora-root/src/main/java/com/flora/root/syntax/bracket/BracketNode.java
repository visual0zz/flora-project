package com.flora.root.syntax.bracket;

import java.util.List;

/**
 * 括号结构节点。
 * <p>由 {@link BracketAnalyzer#analyze(String)} 产出：{@code Text} 为定界符外的被动文本，
 * {@code Group} 为定界符包裹的子结构。Group 只保留结构化 {@code children}，
 * 需要原始内容串时由调用方自行重建。</p>
 */
public sealed interface BracketNode {

    /** 被动文本片段（定界符之外的内容）。 */
    record Text(String text) implements BracketNode {
    }

    /** 括号组：开定界符、解析后的子节点列表、闭定界符。 */
    record Group(String open, List<BracketNode> children, String close) implements BracketNode {
    }
}
