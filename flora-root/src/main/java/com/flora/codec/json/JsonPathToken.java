package com.flora.codec.json;

/** Token 类型枚举。 */
enum TokenType {
    ROOT, CURRENT, DOT, DOT_DOT, STAR,
    LBRACKET, RBRACKET, LPAREN, RPAREN,
    NAME, STRING, NUMBER, COLON, COMMA, QUESTION,
    TRUE, FALSE, NULL,
    EQ, NE, LT, LE, GT, GE,
    AND, OR, NOT,
    FUNCTION, EOF
}

/** Token 记录：类型、值、位置。 */
record Token(TokenType type, String value, int pos) {}
