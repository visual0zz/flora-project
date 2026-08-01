package com.flora.mock.regex.impl;

import java.util.random.RandomGenerator;

/**
 * 字面量原子：每次 emit 输出固定字符。
 */
public final class LiteralAtom extends BaseAtom {

    private final char c;

    public LiteralAtom(char c, RandomGenerator random) {
        super(random);
        this.c = c;
    }

    @Override
    protected void emit(StringBuilder sb) {
        sb.append(c);
    }

    @Override
    public int estimate() {
        return 1;
    }
}
