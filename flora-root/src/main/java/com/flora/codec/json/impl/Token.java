package com.flora.codec.json.impl;

/** Token 记录：类型、值、位置。 */
public record Token(TokenType type, String value, int pos) {}
