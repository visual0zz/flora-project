package com.flora.syntax.peg.impl;

import com.flora.syntax.peg.ParseException;
import com.flora.syntax.peg.Token;
import com.flora.syntax.peg.TokenKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 词法器：把输入切为全部 token 列表（含 {@code kind=SKIP} 的），parser 在显著 token 流上跳过
 * Trivia / SKIP。支持词法模式（mode / pushMode / popMode）：当前模式下只尝试所属该模式的规则，
 * 隐式字面量规则（mode == null）在任意模式都可匹配。最长匹配胜出，平局按声明顺序。
 */
final class Lexer {

    /** 一条 token 规格：名称 + 字符级匹配器 + 类别 + 所属模式 + 模式命令。mode 为 null 表示任意模式可匹配。 */
    record TokenRule(String name, CharMatcher matcher, TokenKind kind, String mode, String modeAction) {
        boolean matchesMode(String current) {
            return mode == null || mode.equals(current);
        }
    }

    private final List<TokenRule> rules;
    private final boolean longestMatch;

    Lexer(List<TokenRule> rules, boolean longestMatch) {
        this.rules = rules;
        this.longestMatch = longestMatch;
    }

    List<TokenRule> rules() { return rules; }

    /** 切词，返回全部 token（含 EOF 哨兵）。无规则匹配的字符即抛词法错误（带位置）。 */
    List<Token> lex(CharSequence in) {
        List<Token> out = new ArrayList<>();
        ArrayDeque<String> modeStack = new ArrayDeque<>();
        String mode = "DEFAULT";
        int pos = 0;
        int line = 1;
        int col = 1;
        while (pos < in.length()) {
            TokenRule best = null;
            int bestLen = -1;
            for (TokenRule tr : rules) {
                if (!tr.matchesMode(mode)) continue;
                int len = tr.matcher().match(in, pos);
                if (len < 0) continue;
                if (longestMatch) {
                    if (len > bestLen) {
                        best = tr;
                        bestLen = len;
                    }
                } else {
                    best = tr;
                    bestLen = len;
                    break;
                }
            }
            if (best == null) {
                throw new ParseException(
                        "无法识别的字符 '" + in.charAt(pos) + "'", line, col, pos, "<lexer>");
            }
            String text = in.subSequence(pos, pos + bestLen).toString();
            out.add(new Token(best.kind(), best.name(), text, pos, pos + bestLen, line, col));
            // 模式命令
            String act = best.modeAction();
            if (act != null) {
                if (act.startsWith("mode:")) {
                    mode = act.substring(5);
                } else if (act.startsWith("pushMode:")) {
                    modeStack.push(mode);
                    mode = act.substring(9);
                } else if (act.equals("popMode")) {
                    if (!modeStack.isEmpty()) mode = modeStack.pop();
                }
            }
            for (int k = 0; k < bestLen; k++) {
                if (text.charAt(k) == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
            }
            pos += bestLen;
        }
        out.add(new Token(new TokenKind.Eof(), "EOF", "", pos, pos, line, col));
        return out;
    }
}
