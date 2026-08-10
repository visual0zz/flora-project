package com.flora.codec.json.path.impl;

/** Token 类型枚举。 */
public enum TokenType {
    ROOT, CURRENT, DOT, DOT_DOT, STAR,
    LBRACKET, RBRACKET, LPAREN, RPAREN,
    NAME, STRING, NUMBER, COLON, COMMA, QUESTION,
    TRUE, FALSE, NULL,
    EQ, NE, LT, LE, GT, GE,
    AND, OR, NOT,
    FUNCTION, EOF
}
