package com.flora.mock.regex.impl;

import java.util.random.RandomGenerator;

/**
 * Unicode 属性原子：从预构建的码点区间池中直接取样，无拒绝采样。
 */
public final class UnicodeClassAtom extends BaseAtom {

    private final int property;
    private final boolean negate;

    public UnicodeClassAtom(int property, boolean negate, RandomGenerator random) {
        super(random);
        this.property = property;
        this.negate = negate;
    }

    @Override
    protected void emit(StringBuilder sb) {
        sb.appendCodePoint(UnicodePropertyRanges.sample(property, negate, random()));
    }

    @Override
    public int estimate() {
        return 1;
    }
}
