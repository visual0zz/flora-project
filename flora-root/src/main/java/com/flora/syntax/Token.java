package com.flora.syntax;

/**
 * 词法单元：类型、原始值、在输入中的起始位置。
 * <p>具体符号（运算符、定界符）的原文存于 {@code value}，{@code type} 只给宽泛类别。</p>
 */
public record Token(TokenType type, String value, int pos) {

    @Override
    public String toString() {
        return type + "(" + value + ")@" + pos;
    }
}
