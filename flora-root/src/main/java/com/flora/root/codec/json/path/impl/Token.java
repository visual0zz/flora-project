package com.flora.root.codec.json.path.impl;

/** Token 记录：类型、值、位置。 */
public record Token(TokenType type, String value, int pos) {}
