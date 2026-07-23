package com.flora.ramet.idea.highlighter;

import com.flora.ramet.idea.lexer.RametTokenTypes;
import com.flora.ramet.idea.parser.RametTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ramet 模板语言的细粒度高亮注解器。
 *
 * <p>在 SyntaxHighlighter 的粗粒度基色之上，对以下元素做范围级细化着色：
 * <ul>
 *   <li>模板指令内：关键字、操作符、字符串、数字、变量、函数名</li>
 *   <li>Meta 注释内：@Param/@Combine/@Path/@Config、Lson key、函数、字面量</li>
 *   <li>TEXT 块内：字符串、注释、数字（粗略分类，暗淡颜色）</li>
 * </ul>
 */
public class RametAnnotator implements Annotator {

    private static final Pattern BUILTIN_FUNC = Pattern.compile(
            "\\b(greaterThan|lessThan|greaterThanOrEquals|lessThanOrEquals|equals|notEquals" +
            "|and|or|not|capital|lower|upper|javaString|concat|contains|replace|startsWith" +
            "|repeat|join|default|notNull|isNull|isEmpty|isBlank|range|sequenceJoin|javaPackageToPath|length)\\b");
    private static final Pattern BOOLEAN_LITERAL = Pattern.compile("\\b(true|false|null)\\b");
    /** 支持 Java 数字后缀 L/F/D（如 123L, 0.2f, 0.0d）。 */
    private static final Pattern NUMBER = Pattern.compile("\\b-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?[lLfFdD]?\\b");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*[^*]*(?:\\*+(?:[^*/][^*]*)?)*\\*+/");
    private static final Pattern META_ANNOTATION = Pattern.compile("@(Param|Cartesian|Combine|Path|Config|SkipWhen)\\b");
    private static final Pattern LSON_KEY = Pattern.compile("\\b([a-zA-Z_]\\w*)\\s*:");
    private static final Pattern FUNC_CALL = Pattern.compile("\\b([a-zA-Z_]\\w*)\\s*\\(");
    private static final Pattern PROPERTY_ACCESS = Pattern.compile("\\.([a-zA-Z_]\\w*)");
    /** 范围运算符 ..（如 <#list idx:1..20> 中的 1..20）。 */
    private static final Pattern RANGE_OP = Pattern.compile("\\.\\.");
    /** 表达式块内的括号 (){}[]（字符串内部除外，着白色）。 */
    private static final Pattern BRACKET = Pattern.compile("[()\\[\\]{}]");

    // 引用名（顶层标识符，非函数、非关键字）
    private static final Pattern REF_NAME = Pattern.compile("\\b([a-zA-Z_]\\w*)\\b");

    // ---- TEXT 块用注解 @Xxx ----
    private static final Pattern TEXT_ANNOTATION = Pattern.compile("@([a-zA-Z_]\\w*)(?:\\.\\w+)*");
    // ---- TEXT 块用函数调用 name( ----
    private static final Pattern TEXT_FUNC_CALL = Pattern.compile("\\b([a-zA-Z_]\\w*)\\s*\\(");
    // ---- TEXT 块用运算符 == != += -= && || < > <= >= ++ -- -> :: ----
    private static final Pattern TEXT_OPERATORS = Pattern.compile("===?|!=|<=|>=|[-+*/%&|^]=?|&&|\\|\\||\\+\\+|--|->|::|[<>]");
    // ---- TEXT 块用泛型/比较尖括号 —— 独立的 < 和 >（非 <= >= 非 ->） ----
    private static final Pattern TEXT_ANGLES = Pattern.compile("(?<![=->])[<>](?!=)");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        var node = element.getNode();
        if (node == null) return;

        var type = node.getElementType();
        String text = element.getText();
        if (text == null || text.isEmpty()) return;

        int offset = element.getTextOffset();

        if (type == RametTypes.DIRECTIVE || type == RametTokenTypes.IF
                || type == RametTokenTypes.FOR || type == RametTokenTypes.INCLUDE
                || type == RametTokenTypes.MACRO || type == RametTokenTypes.ELSE
                || type == RametTokenTypes.ELSEIF
                || type == RametTokenTypes.CONTINUE || type == RametTokenTypes.BREAK
                || type == RametTokenTypes.META) {
            annotateDirective(text, offset, holder);
        } else if (type == RametTypes.MACRO_CALL || type == RametTokenTypes.MACRO_CALL) {
            annotateMacroCall(text, offset, holder);
        } else if (type == RametTypes.VAR_INTERPOLATION || type == RametTokenTypes.VAR) {
            annotateInterpolation(text, offset, holder);
        } else if (type == RametTypes.COMMENT || type == RametTokenTypes.COMMENT) {
            annotateComment(text, offset, holder);
        } else if (type == RametTokenTypes.END) {
            annotateEndTag(text, offset, holder);
        } else if (type == RametTokenTypes.PASSIVE && text.length() > 1) {
            annotateTextBlock(element, text, offset, holder);
        }
    }

    // ========== 模板指令 ==========

    private void annotateDirective(String text, int offset, AnnotationHolder holder) {
        if (text.startsWith("<#")) {
            range(holder, offset, 2, RametSyntaxHighlighter.RAMET_KEYWORD);
            if (text.length() > 2 && Character.isJavaIdentifierPart(text.charAt(2))) {
                int end = 2;
                while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
                range(holder, offset + 2, end - 2, RametSyntaxHighlighter.RAMET_KEYWORD);
            }
        }
        if (text.endsWith(">")) {
            range(holder, offset + text.length() - 1, 1, RametSyntaxHighlighter.RAMET_KEYWORD);
        }
        // meta 指令不包含 Lson 表达式，跳过
        if (text.startsWith("<#meta")) return;

        // 引用着色：<#macro> 的参数为定义，保留橙黄；其余指令（if/for/list/include/elseif/continue/break）
        // 的表达式上下文中的标识符均为引用，着粉红色。
        TextAttributesKey refKey = text.startsWith("<#macro")
                ? RametSyntaxHighlighter.RAMET_LSON_KEY
                : RametSyntaxHighlighter.RAMET_VARIABLE;
        highlightLsonInside(text, offset, holder, true, refKey);
    }

    // ========== 结束标签 </#name> ==========

    private void annotateEndTag(String text, int offset, AnnotationHolder holder) {
        if (text.startsWith("</#")) {
            // 前导 </# 与关键字同色（橙黄），不单独染色
            range(holder, offset, 3, RametSyntaxHighlighter.RAMET_KEYWORD);
            int end = 3;
            while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
            if (end > 3) {
                range(holder, offset + 3, end - 3, RametSyntaxHighlighter.RAMET_KEYWORD);
            }
        }
        if (text.endsWith(">")) {
            range(holder, offset + text.length() - 1, 1, RametSyntaxHighlighter.RAMET_KEYWORD);
        }
    }

    private void annotateMacroCall(String text, int offset, AnnotationHolder holder) {
        if (text.startsWith("<@")) {
            range(holder, offset, 2, RametSyntaxHighlighter.RAMET_KEYWORD);
        }
        int nameEnd = 2;
        while (nameEnd < text.length() && Character.isJavaIdentifierPart(text.charAt(nameEnd))) nameEnd++;
        if (nameEnd > 2) {
            range(holder, offset + 2, nameEnd - 2, RametSyntaxHighlighter.RAMET_FUNCTION);
        }
        String body = nameEnd < text.length() ? text.substring(nameEnd) : "";
        if (!body.isEmpty()) {
            highlightLsonInside(body, offset + nameEnd, holder, true,
                    RametSyntaxHighlighter.RAMET_LSON_KEY);
        }
        if (text.endsWith("/>")) {
            range(holder, offset + text.length() - 2, 2, RametSyntaxHighlighter.RAMET_KEYWORD);
        } else if (text.endsWith(">")) {
            range(holder, offset + text.length() - 1, 1, RametSyntaxHighlighter.RAMET_KEYWORD);
        }
    }

    // ========== ${} 插值 ==========

    private void annotateInterpolation(String text, int offset, AnnotationHolder holder) {
        if (text.startsWith("${")) {
            range(holder, offset, 2, RametSyntaxHighlighter.RAMET_BRACKET);
        }
        if (text.endsWith("}")) {
            range(holder, offset + text.length() - 1, 1, RametSyntaxHighlighter.RAMET_BRACKET);
        }
        String body = text;
        if (body.startsWith("${")) body = body.substring(2);
        if (body.endsWith("}")) body = body.substring(0, body.length() - 1);
        // 变量引用（${...} 内的标识符）着粉红色
        highlightLsonInside(body, offset + (text.startsWith("${") ? 2 : 0), holder, true,
                RametSyntaxHighlighter.RAMET_VARIABLE);
    }

    // ========== 注释 / Meta 块 ==========

    private void annotateComment(String text, int offset, AnnotationHolder holder) {
        // Meta 注解
        Matcher mm = META_ANNOTATION.matcher(text);
        while (mm.find()) {
            range(holder, offset + mm.start(), mm.group().length(), RametSyntaxHighlighter.RAMET_ANNOTATION);
        }

        if (!text.contains("@")) return;

        // 收集字符串区间，用于排除字符串内部的函数调用和数字
        List<int[]> strRanges = new ArrayList<>();
        Matcher sm = STRING_LITERAL.matcher(text);
        while (sm.find()) {
            range(holder, offset + sm.start(), sm.group().length(), RametSyntaxHighlighter.RAMET_STRING);
            strRanges.add(new int[]{sm.start(), sm.end()});
        }

        // Lson key（跳过紧贴 @ 后的名字）
        Matcher km = LSON_KEY.matcher(text);
        while (km.find()) {
            boolean isMeta = km.start() > 0 && text.charAt(km.start() - 1) == '@';
            if (!isMeta) {
                range(holder, offset + km.start(1), km.group(1).length(), RametSyntaxHighlighter.RAMET_LSON_KEY);
            }
        }

        // 函数调用（排除字符串内部）
        Matcher fm = FUNC_CALL.matcher(text);
        while (fm.find()) {
            if (!insideRanges(fm.start(), strRanges)) {
                range(holder, offset + fm.start(1), fm.group(1).length(), RametSyntaxHighlighter.RAMET_FUNCTION);
            }
        }

        // 数字（排除字符串内部）
        Matcher nm = NUMBER.matcher(text);
        while (nm.find()) {
            if (!insideRanges(nm.start(), strRanges)) {
                range(holder, offset + nm.start(), nm.group().length(), RametSyntaxHighlighter.RAMET_NUMBER);
            }
        }

        // 布尔/null
        Matcher bm = BOOLEAN_LITERAL.matcher(text);
        while (bm.find()) {
            if (!insideRanges(bm.start(), strRanges)) {
                range(holder, offset + bm.start(), bm.group().length(), RametSyntaxHighlighter.RAMET_KEYWORD);
            }
        }

        // 引用名（标识符，非关键字/非函数/非 key，排除字符串内部，排除 @Xxx 注解后缀）
        Matcher rm = REF_NAME.matcher(text);
        while (rm.find()) {
            if (insideRanges(rm.start(), strRanges)) continue;
            // 跳过 @Xxx 注解的后缀部分
            if (rm.start() > 0 && text.charAt(rm.start() - 1) == '@') continue;
            String word = rm.group(1);
            if (isKeyword(word) || isFuncCall(text, rm.start())) continue;
            // key 已单独处理，跳过
            if (isLsonKey(text, rm.start())) continue;
            range(holder, offset + rm.start(1), rm.group(1).length(), RametSyntaxHighlighter.RAMET_LSON_KEY);
        }

        // 括号 (){}[]（排除字符串内部，着白色）
        Matcher cm = BRACKET.matcher(text);
        while (cm.find()) {
            if (!insideRanges(cm.start(), strRanges)) {
                range(holder, offset + cm.start(), cm.group().length(), RametSyntaxHighlighter.RAMET_BRACKET);
            }
        }
    }

    // ========== TEXT 块 ==========

    private void annotateTextBlock(PsiElement element, String text, int offset, AnnotationHolder holder) {
        // 检查是否在 <#meta>...</#meta> 块内部
        boolean insideMeta = isInsideMetaBlock(element);
        if (insideMeta) {
            annotateMetaContent(text, offset, holder);
            return;
        }

        // 多语言注释高亮：//（C系列）、#（Python/Shell/Ruby）、
        // --（SQL/Lua）、%（LaTeX/Erlang）、/* */（C系列）
        // 先收集所有注释区间，避免注释内部的内容被其他规则覆盖
        List<int[]> commentRanges = new ArrayList<>();

        // 行注释
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            boolean isLineComment = false;
            if (c == '/' && k + 1 < text.length() && text.charAt(k + 1) == '/') {
                isLineComment = true;
            } else if (c == '#') {
                isLineComment = true;
            } else if (c == '-' && k + 1 < text.length() && text.charAt(k + 1) == '-') {
                isLineComment = true;
            } else if (c == '%') {
                isLineComment = true;
            }
            if (isLineComment) {
                int start = k;
                while (k < text.length() && text.charAt(k) != '\n') k++;
                range(holder, offset + start, k - start, RametSyntaxHighlighter.RAMET_COMMENT);
                commentRanges.add(new int[]{start, k});
            }
        }
        // 块注释：/* */
        for (int k = 0; k < text.length() - 1; k++) {
            if (text.charAt(k) == '/' && text.charAt(k + 1) == '*') {
                int start = k;
                k += 2;
                while (k < text.length() - 1 && !(text.charAt(k) == '*' && text.charAt(k + 1) == '/')) k++;
                if (k < text.length() - 1) k += 2;
                range(holder, offset + start, k - start, RametSyntaxHighlighter.RAMET_COMMENT);
                commentRanges.add(new int[]{start, k});
            }
        }

        // 注解 @Xxx
        Matcher am = TEXT_ANNOTATION.matcher(text);
        while (am.find()) {
            if (!insideRanges(am.start(), commentRanges)) {
                range(holder, offset + am.start(), am.group().length(), RametSyntaxHighlighter.TEXT_ANNOTATION);
            }
        }

        // 函数调用 name(
        Matcher fm = TEXT_FUNC_CALL.matcher(text);
        while (fm.find()) {
            if (!insideRanges(fm.start(), commentRanges)) {
                range(holder, offset + fm.start(1), fm.group(1).length(), RametSyntaxHighlighter.TEXT_FUNCTION);
            }
        }

        // 字符串
        Matcher sm = STRING_LITERAL.matcher(text);
        while (sm.find()) {
            if (!insideRanges(sm.start(), commentRanges)) {
                range(holder, offset + sm.start(), sm.group().length(), RametSyntaxHighlighter.TEXT_STRING);
            }
        }
    }

    // ========== Meta 块 Lson 辅助 ==========

    /**
     * 在包含 {@code @Param/@Cartesian/@Path} 等元数据的 PASSIVE 文本中，
     * 对 Lson key、函数调用、数字、布尔值做高亮着色。
     */
    private void highlightMetaLson(String text, int offset, AnnotationHolder holder, List<int[]> commentRanges) {
        // 收集字符串区间
        List<int[]> strRanges = new ArrayList<>();
        Matcher sm = STRING_LITERAL.matcher(text);
        while (sm.find()) {
            if (!insideRanges(sm.start(), commentRanges)) {
                range(holder, offset + sm.start(), sm.group().length(), RametSyntaxHighlighter.RAMET_STRING);
                strRanges.add(new int[]{sm.start(), sm.end()});
            }
        }

        // Lson key（跳过紧贴 @ 后的名字）
        Matcher km = LSON_KEY.matcher(text);
        while (km.find()) {
            if (insideRanges(km.start(), commentRanges) || insideRanges(km.start(), strRanges)) continue;
            boolean isMeta = km.start() > 0 && text.charAt(km.start() - 1) == '@';
            if (!isMeta) {
                range(holder, offset + km.start(1), km.group(1).length(), RametSyntaxHighlighter.RAMET_LSON_KEY);
            }
        }

        // 函数调用（排除字符串内部）
        Matcher fm = FUNC_CALL.matcher(text);
        while (fm.find()) {
            if (!insideRanges(fm.start(), strRanges) && !insideRanges(fm.start(), commentRanges)) {
                range(holder, offset + fm.start(1), fm.group(1).length(), RametSyntaxHighlighter.RAMET_FUNCTION);
            }
        }

        // 数字（排除字符串内部）
        Matcher nm = NUMBER.matcher(text);
        while (nm.find()) {
            if (!insideRanges(nm.start(), strRanges) && !insideRanges(nm.start(), commentRanges)) {
                range(holder, offset + nm.start(), nm.group().length(), RametSyntaxHighlighter.RAMET_NUMBER);
            }
        }

        // 布尔/null
        Matcher bm = BOOLEAN_LITERAL.matcher(text);
        while (bm.find()) {
            if (!insideRanges(bm.start(), strRanges) && !insideRanges(bm.start(), commentRanges)) {
                range(holder, offset + bm.start(), bm.group().length(), RametSyntaxHighlighter.RAMET_KEYWORD);
            }
        }

        // 引用名（非关键字/非函数/非 key）
        Matcher rm = REF_NAME.matcher(text);
        while (rm.find()) {
            if (insideRanges(rm.start(), strRanges) || insideRanges(rm.start(), commentRanges)) continue;
            if (rm.start() > 0 && text.charAt(rm.start() - 1) == '@') continue;
            String word = rm.group(1);
            if (isKeyword(word) || isFuncCall(text, rm.start())) continue;
            if (isLsonKey(text, rm.start())) continue;
            range(holder, offset + rm.start(1), rm.group(1).length(), RametSyntaxHighlighter.RAMET_VARIABLE);
        }

        // 括号 (){}[]（排除字符串内部）
        Matcher cm = BRACKET.matcher(text);
        while (cm.find()) {
            if (!insideRanges(cm.start(), strRanges) && !insideRanges(cm.start(), commentRanges)) {
                range(holder, offset + cm.start(), cm.group().length(), RametSyntaxHighlighter.RAMET_BRACKET);
            }
        }
    }

    // ========== Meta 块上下文检测 ==========

    /**
     * 判断 PASSIVE 元素是否位于 {@code <#meta>...</#meta>} 块内部。
     * 通过向前遍历 AST 兄弟节点实现：找到 {@code <#meta>} 指令即为块内，
     * 找到 {@code </#meta>} 结束标签则为块外。
     */
    private boolean isInsideMetaBlock(PsiElement element) {
        ASTNode node = element.getNode();
        if (node == null) return false;
        for (ASTNode prev = node.getTreePrev(); prev != null; prev = prev.getTreePrev()) {
            IElementType t = prev.getElementType();
            // 裸 META 关键字
            if (t == RametTokenTypes.META) return true;
            // DIRECTIVE 节点包含 META 关键字
            if (t == RametTypes.DIRECTIVE) {
                for (ASTNode c : prev.getChildren(null)) {
                    if (c.getElementType() == RametTokenTypes.META) return true;
                }
            }
            // </#meta> 关闭元数据块
            if (t == RametTokenTypes.END && prev.getText() != null
                    && prev.getText().contains("meta")) {
                return false;
            }
        }
        return false;
    }

    /**
     * 对 {@code <#meta>...</#meta>} 块内的 PASSIVE 文本使用鲜艳语法色高亮
     * （Lson key、函数、字符串、数字、布尔、括号），而非默认的暗淡色。
     */
    private void annotateMetaContent(String text, int offset, AnnotationHolder holder) {
        // 收集字符串区间
        List<int[]> strRanges = new ArrayList<>();
        Matcher sm = STRING_LITERAL.matcher(text);
        while (sm.find()) {
            range(holder, offset + sm.start(), sm.group().length(), RametSyntaxHighlighter.RAMET_STRING);
            strRanges.add(new int[]{sm.start(), sm.end()});
        }

        // Meta 注解 @Param/@Cartesian/@Path/@SkipWhen 等
        Matcher mm = META_ANNOTATION.matcher(text);
        while (mm.find()) {
            if (!insideRanges(mm.start(), strRanges)) {
                range(holder, offset + mm.start(), mm.group().length(), RametSyntaxHighlighter.RAMET_ANNOTATION);
            }
        }

        // Lson key（跳过紧贴 @ 后的名字）
        Matcher km = LSON_KEY.matcher(text);
        while (km.find()) {
            if (insideRanges(km.start(), strRanges)) continue;
            boolean isMeta = km.start() > 0 && text.charAt(km.start() - 1) == '@';
            if (!isMeta) {
                range(holder, offset + km.start(1), km.group(1).length(), RametSyntaxHighlighter.RAMET_LSON_KEY);
            }
        }

        // 内置函数
        Matcher bm2 = BUILTIN_FUNC.matcher(text);
        while (bm2.find()) {
            if (!insideRanges(bm2.start(), strRanges)) {
                range(holder, offset + bm2.start(), bm2.group().length(), RametSyntaxHighlighter.RAMET_FUNCTION);
            }
        }

        // 函数调用 name(
        Matcher fm = FUNC_CALL.matcher(text);
        while (fm.find()) {
            if (!insideRanges(fm.start(), strRanges)) {
                range(holder, offset + fm.start(1), fm.group(1).length(), RametSyntaxHighlighter.RAMET_FUNCTION);
            }
        }

        // 数字
        Matcher nm = NUMBER.matcher(text);
        while (nm.find()) {
            if (!insideRanges(nm.start(), strRanges)) {
                range(holder, offset + nm.start(), nm.group().length(), RametSyntaxHighlighter.RAMET_NUMBER);
            }
        }

        // 布尔/null
        Matcher bm3 = BOOLEAN_LITERAL.matcher(text);
        while (bm3.find()) {
            if (!insideRanges(bm3.start(), strRanges)) {
                range(holder, offset + bm3.start(), bm3.group().length(), RametSyntaxHighlighter.RAMET_KEYWORD);
            }
        }

        // 范围运算符 ..
        Matcher rom = RANGE_OP.matcher(text);
        while (rom.find()) {
            if (!insideRanges(rom.start(), strRanges)) {
                range(holder, offset + rom.start(), rom.group().length(), RametSyntaxHighlighter.RAMET_OPERATOR);
            }
        }

        // 逗号
        Pattern COMMA = Pattern.compile(",");
        Matcher comma = COMMA.matcher(text);
        while (comma.find()) {
            if (!insideRanges(comma.start(), strRanges)) {
                range(holder, offset + comma.start(), 1, RametSyntaxHighlighter.RAMET_BRACKET);
            }
        }

        // 冒号（不在 Lson key 位置上的冒号，如 { type: "int" } 中的冒号本身）
        // 注意 Lson key 的 \s*: 部分已经随 key 匹配了冒号后面的空格，但冒号本身未被着色，
        // 这里单独给冒号着色
        Pattern COLON = Pattern.compile(":");
        Matcher col = COLON.matcher(text);
        while (col.find()) {
            if (!insideRanges(col.start(), strRanges)) {
                range(holder, offset + col.start(), 1, RametSyntaxHighlighter.RAMET_OPERATOR);
            }
        }

        // 点号（属性访问符）
        Matcher dt = java.util.regex.Pattern.compile("\\.").matcher(text);
        while (dt.find()) {
            if (!insideRanges(dt.start(), strRanges)) {
                range(holder, offset + dt.start(), 1, RametSyntaxHighlighter.RAMET_OPERATOR);
            }
        }

        // 括号 (){}[]
        Matcher cm = BRACKET.matcher(text);
        while (cm.find()) {
            if (!insideRanges(cm.start(), strRanges)) {
                range(holder, offset + cm.start(), cm.group().length(), RametSyntaxHighlighter.RAMET_BRACKET);
            }
        }

        // 引用名（非关键字/非函数/非 key）
        Matcher rm = REF_NAME.matcher(text);
        while (rm.find()) {
            if (insideRanges(rm.start(), strRanges)) continue;
            if (rm.start() > 0 && text.charAt(rm.start() - 1) == '@') continue;
            String word = rm.group(1);
            if (isKeyword(word) || isFuncCall(text, rm.start())) continue;
            if (isLsonKey(text, rm.start())) continue;
            range(holder, offset + rm.start(1), rm.group(1).length(), RametSyntaxHighlighter.RAMET_VARIABLE);
        }
    }

    // ========== Lson 表达式内部辅助 ==========

    private void highlightLsonInside(String text, int offset, AnnotationHolder holder,
                                      boolean bright, TextAttributesKey refKey) {
        if (text.isEmpty()) return;

        TextAttributesKey strKey = bright ? RametSyntaxHighlighter.RAMET_STRING : RametSyntaxHighlighter.TEXT_STRING;
        TextAttributesKey numKey = bright ? RametSyntaxHighlighter.RAMET_NUMBER : RametSyntaxHighlighter.TEXT_NUMBER;
        TextAttributesKey kwKey  = bright ? RametSyntaxHighlighter.RAMET_KEYWORD : RametSyntaxHighlighter.TEXT_NUMBER;
        TextAttributesKey funcKey = bright ? RametSyntaxHighlighter.RAMET_FUNCTION : RametSyntaxHighlighter.TEXT_STRING;

        // 收集字符串区间
        List<int[]> strRanges = new ArrayList<>();
        Matcher sm = STRING_LITERAL.matcher(text);
        while (sm.find()) {
            range(holder, offset + sm.start(), sm.group().length(), strKey);
            strRanges.add(new int[]{sm.start(), sm.end()});
        }

        // 运算符/内置函数（统一着蓝色，排除字符串内部）
        Matcher bm = BUILTIN_FUNC.matcher(text);
        while (bm.find()) {
            if (!insideRanges(bm.start(), strRanges)) {
                range(holder, offset + bm.start(), bm.group().length(), funcKey);
            }
        }

        // 范围运算符 ..（排除字符串内部，着蓝色）
        Matcher rom = RANGE_OP.matcher(text);
        while (rom.find()) {
            if (!insideRanges(rom.start(), strRanges)) {
                range(holder, offset + rom.start(), rom.group().length(), funcKey);
            }
        }

        // 括号 (){}[]（排除字符串内部，着白色）
        Matcher bm2 = BRACKET.matcher(text);
        while (bm2.find()) {
            if (!insideRanges(bm2.start(), strRanges)) {
                range(holder, offset + bm2.start(), bm2.group().length(), RametSyntaxHighlighter.RAMET_BRACKET);
            }
        }

        // 布尔/null（排除字符串内部）
        Matcher bom = BOOLEAN_LITERAL.matcher(text);
        while (bom.find()) {
            if (!insideRanges(bom.start(), strRanges)) {
                range(holder, offset + bom.start(), bom.group().length(), kwKey);
            }
        }

        // 数字（排除字符串内部）
        Matcher nm = NUMBER.matcher(text);
        while (nm.find()) {
            if (!insideRanges(nm.start(), strRanges)) {
                range(holder, offset + nm.start(), nm.group().length(), numKey);
            }
        }

        // 函数调用 name(（排除字符串内部）
        Matcher fm = FUNC_CALL.matcher(text);
        while (fm.find()) {
            if (!insideRanges(fm.start(), strRanges)) {
                range(holder, offset + fm.start(1), fm.group(1).length(), funcKey);
            }
        }

        // 属性访问 .prop（排除字符串内部）
        Matcher pm = PROPERTY_ACCESS.matcher(text);
        while (pm.find()) {
            if (!insideRanges(pm.start(), strRanges)) {
                range(holder, offset + pm.start(1), pm.group(1).length(), refKey);
            }
        }

        // 引用名：标识符，非关键字、非函数、非属性链
        Matcher rm = REF_NAME.matcher(text);
        while (rm.find()) {
            if (insideRanges(rm.start(), strRanges)) continue;
            String word = rm.group(1);
            if (isKeyword(word) || isFuncCall(text, rm.start())) continue;
            // 跳过属性链的右侧部分（已被 PROPERTY_ACCESS 处理）
            if (rm.start() > 0 && text.charAt(rm.start() - 1) == '.') continue;
            range(holder, offset + rm.start(1), rm.group(1).length(), refKey);
        }
    }

    // ========== 工具 ==========

    private static boolean insideRanges(int pos, List<int[]> ranges) {
        for (var r : ranges) {
            if (pos >= r[0] && pos < r[1]) return true;
        }
        return false;
    }

    private static boolean isKeyword(String word) {
        return switch (word) {
            case "true", "false", "null",
                 "greaterThan", "lessThan", "greaterThanOrEquals", "lessThanOrEquals",
                 "equals", "notEquals", "and", "or", "not",
                 "capital", "lower", "upper", "javaString", "concat", "contains",
                 "replace", "startsWith", "repeat", "join", "default",
                 "notNull", "isNull", "isEmpty", "isBlank",
                 "range", "sequenceJoin", "length", "javaPackageToPath",
                 "plus", "minus", "now",
                 "selfCartesian", "permutation", "combination",
                 "multiCombination", "cartesian", "concatList", "concatField", "sortBy",
                 "if", "else", "for", "list", "include", "macro", "as",
                 "continue", "break", "meta" -> true;
            default -> false;
        };
    }

    /** 检查位置是否为一个函数调用名（后面有 (）。 */
    private static boolean isFuncCall(String text, int pos) {
        int idx = pos + 1; // 跳过标识符起始
        while (idx < text.length() && Character.isJavaIdentifierPart(text.charAt(idx))) idx++;
        // 跳过空白
        while (idx < text.length() && Character.isWhitespace(text.charAt(idx))) idx++;
        return idx < text.length() && text.charAt(idx) == '(';
    }

    /** 检查位置是否为一个 Lson key（后面有 :）。 */
    private static boolean isLsonKey(String text, int pos) {
        int idx = pos + 1;
        while (idx < text.length() && Character.isJavaIdentifierPart(text.charAt(idx))) idx++;
        while (idx < text.length() && Character.isWhitespace(text.charAt(idx))) idx++;
        return idx < text.length() && text.charAt(idx) == ':';
    }

    private static void range(AnnotationHolder holder, int start, int length, TextAttributesKey key) {
        if (length <= 0) return;
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange.create(start, start + length))
                .textAttributes(key)
                .create();
    }
}
