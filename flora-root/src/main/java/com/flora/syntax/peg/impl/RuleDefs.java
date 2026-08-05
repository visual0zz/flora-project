package com.flora.syntax.peg.impl;

import java.util.List;

/**
 * 语法定义 AST：一份 g4 子集文法的规则集。
 *
 * <p>元解析器（{@link MetaParser}）把语法定义字符串解析为 {@link RuleDef} 列表；校验与编译
 * （{@link Validator} / {@link Compiler}）再据此生成词法器与 token 级 PEG。
 */
public final class RuleDefs {

    /** 规则体元素。同一棵 AST 同时服务词法与文法层：词法层按"字符"解释，文法层按"token"解释。 */
    public sealed interface Elem {}

    /** 字符串字面量 {@code '...'}。词法层：字面量字符；文法层：隐式 token（按文本匹配）。 */
    public record ELit(String text) implements Elem {}

    /** 规则引用。词法层引用大写词法/fragment 规则；文法层引用大写 token 或小写文法规则。 */
    public record ERef(String name) implements Elem {}

    /** 字符类 {@code [...]} / {@code ~[...]}（仅词法层）。inner 为方括号内原文，negated 为是否取反。 */
    public record EClass(String inner, boolean negated) implements Elem {}

    /** 任意字符 {@code .}（仅词法层）。 */
    public record EAny() implements Elem {}

    /** 分组 {@code ( alts )}。文法层透明（不产节点）。 */
    public record EGroup(List<Alt> alts) implements Elem {}

    /** 重复 {@code *} / {@code +} / {@code ?}；max == -1 表示无上限。 */
    public record ERepeat(Elem elem, int min, int max) implements Elem {}

    /** 句法前瞻 {@code &e}（不消费）。 */
    public record EAnd(Elem elem) implements Elem {}

    /** 负前瞻 {@code !e}（不消费）。 */
    public record ENot(Elem elem) implements Elem {}

    /** 一个候选：元素序列 + 可选 {@code #Label}。 */
    public record Alt(List<Elem> elems, String label) {}

    /**
     * 一条规则。lexer 为真表示大写开头的词法规则；fragment 为真表示 {@code fragment} 辅助规则。
     * mode 为该规则所属词法模式（null = DEFAULT）；modeAction 为词法模式命令（"mode:X" / "pushMode:X" / "popMode" / null）。
     */
    public record RuleDef(String name, boolean lexer, boolean fragment,
                          List<Alt> alts, String kindName, String mode, String modeAction) {}

    /** 该元素能否在"首位置"匹配空（零宽）。ERef 保守视为不可空，避免递归。 */
    public static boolean nullableFirst(Elem e) {
        return switch (e) {
            case EAnd a -> true;
            case ENot n -> true;
            case ERepeat rep -> rep.min() == 0 || nullableFirst(rep.elem());
            case EGroup g -> {
                boolean any = false;
                for (Alt a : g.alts()) {
                    boolean all = true;
                    for (Elem ee : a.elems()) {
                        if (!nullableFirst(ee)) {
                            all = false;
                            break;
                        }
                    }
                    if (all) {
                        any = true;
                        break;
                    }
                }
                yield any;
            }
            case ERef ref -> false;
            default -> false;
        };
    }
}
