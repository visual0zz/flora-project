package com.flora.mock.regex.impl;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 分组原子：每次 emit 随机选一个交替分支，按分支内原子顺序输出。
 */
public final class GroupAtom extends BaseAtom {

    private final List<List<RegexAtom>> alternatives;

    public GroupAtom(List<List<RegexAtom>> alternatives, RandomGenerator random) {
        super(random);
        this.alternatives = alternatives;
    }

    @Override
    protected void emit(StringBuilder sb) {
        List<RegexAtom> branch = alternatives.get(random().nextInt(alternatives.size()));
        for (RegexAtom atom : branch) {
            atom.append(sb);
        }
    }

    /** 分支内原子长度之和的平均值作为分组单次长度。 */
    @Override
    public int estimate() {
        long sum = 0;
        for (List<RegexAtom> branch : alternatives) {
            for (RegexAtom atom : branch) {
                sum += atom.estimate();
            }
        }
        return (int) Math.max(1, sum / alternatives.size());
    }
}
