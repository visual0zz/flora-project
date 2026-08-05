package com.flora.syntax.peg;

/** 文法编译期错误（未定义规则引用、词法规则体非法、左递归、可匹配空串的词法规则等），由 compile 抛出。 */
public final class GrammarException extends RuntimeException {
    public GrammarException(String message) {
        super(message);
    }
}
