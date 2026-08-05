/**
 * 词法定义：统一 token 与其类别词汇表。
 * <p>{@code Token} 携带通用类别 {@code TokenKind}、文法特指名 {@code typeName}、
 * 原文 {@code text} 与位置（start/end/line/column）；{@code TokenKind} 为密封的
 * 固定词汇表。PEG 引擎与简单词法器（{@code com.flora.syntax.Tokenizer}）共用此定义。</p>
 */
package com.flora.syntax.definition;
