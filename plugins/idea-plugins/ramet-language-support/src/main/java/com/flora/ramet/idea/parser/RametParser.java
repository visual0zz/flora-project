package com.flora.ramet.idea.parser;

import com.flora.ramet.idea.lexer.RametTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Ramet 模板语言的 PsiParser。
 *
 * <p>从细粒度 token 流构建有层次的 PSI 树：
 * <ul>
 *   <li>{@code LT HASH IF ... GT} → {@code DIRECTIVE}</li>
 *   <li>{@code LT IDENTIFIER ... GT} → {@code MACRO_CALL}</li>
 *   <li>{@code DOLLAR LBRACE ... RBRACE} → {@code VAR_INTERPOLATION}</li>
 *   <li>{@code COMMENT} → {@code COMMENT}</li>
 *   <li>其余 token 保留为叶子节点。</li>
 * </ul>
 */
public class RametParser implements PsiParser {

    /** 最大 token 消费数：防止意外输入导致解析器死循环。 */
    private static final int MAX_TOKENS = 100000;

    @Override
    public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        builder.setDebugMode(false);

        PsiBuilder.Marker fileMarker = builder.mark();
        while (!builder.eof()) {
            parseElement(builder);
        }
        // 根节点必须使用传入的 IFileElementType（与 ParserDefinition.getFileNodeType() 一致），
        // 否则 reparse 时 DiffLog 会因节点类型不匹配抛出 PsiInvalidElementAccessException，导致文件打不开。
        fileMarker.done(root);

        return builder.getTreeBuilt();
    }

    private void parseElement(PsiBuilder builder) {
        IElementType tt = builder.getTokenType();
        if (tt == null) {
            builder.advanceLexer();
            return;
        }

        // ---- 指令：<#keyword args> ----
        if (tt == RametTokenTypes.LT) {
            PsiBuilder.Marker marker = builder.mark();
            builder.advanceLexer(); // consume LT
            IElementType next = builder.getTokenType();

            if (next == RametTokenTypes.HASH) {
                // 指令：<# keyword args... >
                builder.advanceLexer(); // consume HASH
                consumeDirectiveBody(builder);
                marker.done(RametTypes.DIRECTIVE);
                return;
            }

            // 宏调用：<@ name args... >   — 关键：<@ 发出 LT 后下一个 token 是 IDENTIFIER（宏名）
            if (next == RametTokenTypes.IDENTIFIER) {
                builder.advanceLexer(); // consume IDENTIFIER (macro name)
                consumeMacroCallBody(builder);
                marker.done(RametTypes.MACRO_CALL);
                return;
            }

            // 既非指令也非宏调用 → 孤立 LT
            marker.drop();
            return;
        }

        // ---- 变量插值：${expr} ----
        if (tt == RametTokenTypes.DOLLAR) {
            PsiBuilder.Marker marker = builder.mark();
            builder.advanceLexer(); // consume DOLLAR
            if (builder.getTokenType() == RametTokenTypes.LBRACE) {
                builder.advanceLexer(); // consume LBRACE
                consumeVarBody(builder);
                marker.done(RametTypes.VAR_INTERPOLATION);
            } else {
                marker.drop();
            }
            return;
        }

        // ---- 注释 ----
        if (tt == RametTokenTypes.COMMENT) {
            PsiBuilder.Marker marker = builder.mark();
            builder.advanceLexer();
            marker.done(RametTypes.COMMENT);
            return;
        }

        // ---- 其他：保持为叶子（PASSIVE, NEWLINE, END, 单字符等） ----
        builder.advanceLexer();
    }

    /** 消费指令体：从 keyword 开始到 >（含）。追踪括号深度以处理 {@code (x > 0)}。 */
    private void consumeDirectiveBody(PsiBuilder builder) {
        int depth = 0;
        int safety = 0;
        while (!builder.eof()) {
            if (++safety > MAX_TOKENS) break;
            IElementType tt = builder.getTokenType();
            if (tt == null) break;

            if (tt == RametTokenTypes.PAREN_L) {
                depth++;
                builder.advanceLexer();
            } else if (tt == RametTokenTypes.PAREN_R) {
                depth--;
                builder.advanceLexer();
            } else if (tt == RametTokenTypes.LBRACE) {
                depth++;
                builder.advanceLexer();
            } else if (tt == RametTokenTypes.RBRACE) {
                depth--;
                builder.advanceLexer();
            } else if (tt == RametTokenTypes.GT && depth == 0) {
                // > 在深度 0 表示指令结束
                if (builder.getTokenType() == RametTokenTypes.SLASH) {
                    // /> 自闭合
                    builder.advanceLexer(); // consume SLASH
                }
                builder.advanceLexer(); // consume GT
                return;
            } else {
                builder.advanceLexer();
            }
        }
    }

    /** 消费宏调用体：宏名之后的 args + > 或 />。 */
    private void consumeMacroCallBody(PsiBuilder builder) {
        int depth = 0;
        int safety = 0;
        while (!builder.eof()) {
            if (++safety > MAX_TOKENS) break;
            IElementType tt = builder.getTokenType();
            if (tt == null) break;

            if (tt == RametTokenTypes.PAREN_L) {
                depth++;
                builder.advanceLexer();
            } else if (tt == RametTokenTypes.PAREN_R) {
                depth--;
                builder.advanceLexer();
            } else if (tt == RametTokenTypes.LBRACE) {
                depth++;
                builder.advanceLexer();
            } else if (tt == RametTokenTypes.RBRACE) {
                depth--;
                builder.advanceLexer();
            } else if (tt == RametTokenTypes.GT && depth == 0) {
                builder.advanceLexer(); // consume GT
                return;
            } else if (tt == RametTokenTypes.SLASH && depth == 0) {
                // 可能为 />
                int savedPos = builder.getCurrentOffset();
                builder.advanceLexer(); // consume SLASH
                if (builder.getTokenType() == RametTokenTypes.GT) {
                    builder.advanceLexer(); // consume GT
                    return;
                }
                // 不是 />，回退（PsiBuilder 不支持回退，所以只能继续）
                // 这种情况极少见，直接返回，退出循环
                return;
            } else {
                builder.advanceLexer();
            }
        }
    }

    /** 消费变量体：从 expr 起遇到 RBRACE 为止。退出时 RBRACE 已被消费。 */
    private void consumeVarBody(PsiBuilder builder) {
        int depth = 1; // 已有的 LBRACE 嵌套深度
        int safety = 0;
        while (!builder.eof()) {
            if (++safety > MAX_TOKENS) break;
            IElementType tt = builder.getTokenType();
            if (tt == null) break;

            if (tt == RametTokenTypes.PAREN_L) {
                depth++;
                builder.advanceLexer();
            } else if (tt == RametTokenTypes.PAREN_R) {
                depth--;
                builder.advanceLexer();
            } else if (tt == RametTokenTypes.LBRACE) {
                depth++;
                builder.advanceLexer();
            } else if (tt == RametTokenTypes.RBRACE) {
                depth--;
                builder.advanceLexer(); // consume RBRACE
                if (depth == 0) return; // outer ${} closed
            } else {
                builder.advanceLexer();
            }
        }
    }
}
