package com.flora.mock.regex.impl;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 字符类原子：每次 emit 从候选字符集合中随机选一个。
 */
public final class CharClassAtom extends BaseAtom {

    private final List<Character> chars;

    public CharClassAtom(List<Character> chars, RandomGenerator random) {
        super(random);
        this.chars = chars;
    }

    @Override
    protected void emit(StringBuilder sb) {
        sb.append(chars.get(random().nextInt(chars.size())));
    }

    @Override
    public int estimate() {
        return 1;
    }
}
