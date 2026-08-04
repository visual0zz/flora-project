package com.flora.codegen.engine.parser;

import com.flora.codegen.engine.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * 空白规整：在词法分析之后、语法分析之前运行，消除「独占一行的指令」所遗留的前导空白与多余换行。
 *
 * <p>词法阶段已把每个换行符及其后的连续水平空白打包成一个 {@link Token.Type#NEW_LINE} token。
 * 本类针对「独占一行」的指令（前后均为 {@code NEW_LINE} 或流边界）做两件事：
 * <ul>
 *   <li><b>lstrip</b>：清空其前一个 {@code NEW_LINE} 的缩进段（保留换行，因换行属于上一行）；</li>
 *   <li><b>trim</b>：清空其后一个 {@code NEW_LINE} 的换行段（保留缩进，因缩进属于下一行）。</li>
 * </ul>
 * 即只剥掉指令「自己那一行」的部分，相邻行的换行与缩进均保留。处理完成后再把 {@code NEW_LINE}
 * 折叠为 {@link Token.Type#PASSIVE}，使语法分析阶段完全不感知空白。
 *
 * <p>指令是否「独占一行」需同时看前后两侧：仅当前一个是 {@code NEW_LINE}（或流首纯空白）且后一个
 * 是 {@code NEW_LINE}（或流尾）时才规整。否则视为内联指令，不触碰周围的换行。
 */
public final class WhitespaceTrimmer {

    private WhitespaceTrimmer() {
    }

    /** 判断是否为指令类 token（独占一行时应被规整）。 */
    private static boolean isDirective(Token.Type type) {
        return switch (type) {
            case IF, FOR, ELSE, ELSEIF, END, MACRO, MACRO_CALL,
                 INCLUDE, COMMENT, META, CONTINUE, BREAK -> true;
            default -> false;
        };
    }

    /**
     * 规整 token 流。返回新列表，不修改入参。
     */
    public static List<Token> trim(List<Token> in) {
        int n = in.size();
        boolean[] clearNl = new boolean[n];       // 对应 NEW_LINE：清空换行段
        boolean[] clearIndent = new boolean[n];   // 对应 NEW_LINE：清空缩进段
        boolean[] dropPassive = new boolean[n];   // 文件开头纯空白 PASSIVE：整段删除

        for (int k = 0; k < n; k++) {
            Token t = in.get(k);
            if (!isDirective(t.type())) continue;

            boolean atLineStart = k == 0
                    || in.get(k - 1).type() == Token.Type.NEW_LINE
                    || (in.get(k - 1).type() == Token.Type.PASSIVE && in.get(k - 1).text().isBlank());
            boolean atLineEnd = k == n - 1 || in.get(k + 1).type() == Token.Type.NEW_LINE;
            if (!atLineStart || !atLineEnd) continue;

            // 独占一行指令：降级其前后 NEW_LINE
            if (k > 0 && in.get(k - 1).type() == Token.Type.NEW_LINE) clearIndent[k - 1] = true;
            if (k < n - 1 && in.get(k + 1).type() == Token.Type.NEW_LINE) clearNl[k + 1] = true;
            // 文件开头纯空白（无前导换行时的行首）：整段删除
            if (k > 0 && in.get(k - 1).type() == Token.Type.PASSIVE) dropPassive[k - 1] = true;
        }

        List<Token> out = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            Token t = in.get(k);
            if (t.type() == Token.Type.NEW_LINE) {
                String text = t.text();
                int nlEnd = newlineEnd(text);
                String nl = clearNl[k] ? "" : text.substring(0, nlEnd);
                String indent = clearIndent[k] ? "" : text.substring(nlEnd);
                String merged = nl + indent;
                if (!merged.isEmpty()) {
                    out.add(new Token(Token.Type.PASSIVE, merged, t.line(), t.col()));
                }
            } else if (dropPassive[k]) {
                // 文件开头纯空白 PASSIVE 已被规整删除
            } else {
                out.add(t);
            }
        }
        return out;
    }

    /** 返回换行子串的结束索引（{@code \r\n} 计为一个）。 */
    private static int newlineEnd(String text) {
        if (text.startsWith("\r\n")) return 2;
        return 1;
    }
}
