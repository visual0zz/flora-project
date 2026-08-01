package com.flora.mock.automaton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * 正则自动机：正则文本的编译结果，提供匹配、采样与代数运算。
 * <p>编译后在 {@code Dfa} 上工作。采样为确定性一次成型（无拒绝采样）：
 * 每次转移只选"仍能到达接受状态"的边，从字符区间加权取随机字符。</p>
 *
 * <p><b>接受的语法</b>：字面量、{@code .}（除行终止符外任意字符）、
 * 字符类 {@code [a-z]}/{@code [^...]}/范围/内嵌简写/嵌套字符类 {@code [a-z[0-9]]}/
 * 交集 {@code [a-z&&[^aeiou]]}、简写 {@code \d \w \s \D \W \S}、
 * 转义 {@code \t \n \r \f \0}、十六进制 {@code \x{1F}}/{@code \xNN}、
 * Unicode 转义 {@code \u0041}、Unicode 属性 {@code \p{L}}/{@code \P{L}}、
 * 量词 {@code * + ? {n} {n,m} {n,}}（懒惰后缀 {@code ?} 忽略）、
 * 分组与交替 {@code (a|b)}、非捕获组 {@code (?:...)}；锚 {@code ^}/{@code $} 忽略。</p>
 *
 * <p><b>不接受的语法</b>（编译期抛 {@link AutomatonException}，不做静默处理）：
 * 反向引用 {@code \1}、环视 {@code (?=...)}/{@code (?!...)}/{@code (?<=...)}、
 * 命名组 {@code (?<name>...)}、未知 Unicode 属性、非法/超阈值量词
 * （重复上限 256）、未闭合的字符类/分组/量词。</p>
 *
 * <pre>{@code
 * Automaton a = Automaton.compile("[a-z]{2,4}");
 * String s = a.sample(20, random);   // 长度接近 20
 * boolean ok = a.matches("abc");
 * }</pre>
 */
public final class Automaton {

    private final Dfa dfa;
    private final int[] minLen;

    private Automaton(Dfa dfa) {
        this.dfa = dfa;
        this.minLen = dfa.minLen();
    }

    /** 编译正则文本。不兼容语法抛 {@link AutomatonException}。 */
    public static Automaton compile(String pattern) {
        return new Automaton(Dfa.fromNfa(RegexCompiler.compile(pattern)));
    }

    /** 该自动机是否可满足（存在至少一个匹配串）。 */
    public boolean isSatisfiable() {
        return minLen[dfa.start()] >= 0;
    }

    /** 指定字符串是否被接受。 */
    public boolean matches(String s) {
        int state = dfa.start();
        for (int i = 0; i < s.length(); i++) {
            int cp = s.codePointAt(i);
            int width = Character.charCount(cp);
            if (width == 2) {
                return false; // 超出 BMP 字符不支持
            }
            Map<CharSet, Integer> out = dfa.transitionsOf(state);
            Integer next = null;
            for (Map.Entry<CharSet, Integer> e : out.entrySet()) {
                if (e.getKey().contains(cp)) {
                    next = e.getValue();
                    break;
                }
            }
            if (next == null) {
                return false;
            }
            state = next;
        }
        return dfa.isAccept(state);
    }

    /** 自然采样：随机生成一个匹配串（无目标长度）。 */
    public String sample(RandomGenerator random) {
        return sample(-1, random);
    }

    /**
     * 按目标长度采样：长度尽量接近 targetLength（&lt; 0 表示无目标）。
     * 无拒绝采样——每步只选能到达接受状态的边。
     */
    public String sample(int targetLength, RandomGenerator random) {
        if (!isSatisfiable()) {
            throw new AutomatonException("该正则语言为空，无法生成");
        }
        // 目标长度小于语言最小长度时，提升到最小可行长度（硬约束优先）
        if (targetLength >= 0 && targetLength < minLen[dfa.start()]) {
            targetLength = minLen[dfa.start()];
        }
        StringBuilder sb = new StringBuilder();
        int state = dfa.start();
        int remaining = targetLength;
        while (true) {
            if (dfa.isAccept(state) && remaining == 0) {
                break;
            }
            if (dfa.isAccept(state) && remaining < 0) {
                // 无目标：随机决定是否在此终止
                if (random.nextBoolean()) {
                    break;
                }
            }
            Map<CharSet, Integer> out = dfa.transitionsOf(state);
            // 候选边：还能到达接受状态（minLen[to] <= remaining-1）
            List<Map.Entry<CharSet, Integer>> candidates = new ArrayList<>();
            for (Map.Entry<CharSet, Integer> e : out.entrySet()) {
                int to = e.getValue();
                int need = remaining >= 0 ? remaining - 1 : -1;
                if (minLen[to] >= 0 && (need < 0 || minLen[to] <= need)) {
                    candidates.add(e);
                }
            }
            if (candidates.isEmpty()) {
                // 必须终止（已到接受且剩余不足）；若当前非接受则语言矛盾
                if (dfa.isAccept(state)) {
                    break;
                }
                throw new AutomatonException("生成失败：无法继续展开");
            }
            Map.Entry<CharSet, Integer> pick = candidates.get(random.nextInt(candidates.size()));
            CharSet cs = pick.getKey();
            long size = cs.size();
            long idx = (long) (random.nextDouble() * size);
            sb.appendCodePoint(cs.codePointAt(idx));
            state = pick.getValue();
            if (remaining >= 0) {
                remaining--;
            }
        }
        return sb.toString();
    }

    /** 估算典型长度（供上层预算分配参考）。 */
    public int estimateLength() {
        if (!isSatisfiable()) {
            return 0;
        }
        // 从起始状态随机游走到接受状态，取路径长度
        int state = dfa.start();
        int len = 0;
        while (!dfa.isAccept(state) || (len == 0 && dfa.isAccept(state))) {
            if (dfa.isAccept(state)) {
                break;
            }
            Map<CharSet, Integer> out = dfa.transitionsOf(state);
            List<Map.Entry<CharSet, Integer>> candidates = new ArrayList<>();
            for (Map.Entry<CharSet, Integer> e : out.entrySet()) {
                if (minLen[e.getValue()] >= 0) {
                    candidates.add(e);
                }
            }
            if (candidates.isEmpty()) {
                break;
            }
            int pick = (int) (Math.random() * candidates.size());
            state = candidates.get(pick).getValue();
            len++;
        }
        return len;
    }

    /** 补集：接受该语言补集的自动机。 */
    public Automaton complement() {
        Dfa base = complete(dfa);
        Dfa d = new Dfa();
        // 复制 DFA 并翻转接受状态
        for (int i = 0; i < base.stateCount(); i++) {
            d.newState();
        }
        d.setStart(base.start());
        for (int s = 0; s < base.stateCount(); s++) {
            for (Map.Entry<CharSet, Integer> e : base.transitionsOf(s).entrySet()) {
                d.addTransition(s, e.getKey(), e.getValue());
            }
            if (!base.isAccept(s)) {
                d.addAccept(s);
            }
        }
        return new Automaton(d);
    }

    /** 交集：接受同时属于两语言字符串的自动机（product construction）。 */
    public Automaton intersect(Automaton other) {
        return product(other, true);
    }

    /** 并集：接受属于任一语言字符串的自动机。 */
    public Automaton union(Automaton other) {
        return product(other, false);
    }

    private Automaton product(Automaton other, boolean and) {
        // 补全两 DFA 为完全确定（未覆盖字符 → sink 自环），product 直接取交
        Dfa a = complete(dfa);
        Dfa b = complete(other.dfa);
        int nb = b.stateCount();
        Dfa d = new Dfa();
        for (int sa = 0; sa < a.stateCount(); sa++) {
            for (int sb = 0; sb < nb; sb++) {
                d.newState();
            }
        }
        d.setStart(a.start() * nb + b.start());
        for (int sa = 0; sa < a.stateCount(); sa++) {
            for (int sb = 0; sb < nb; sb++) {
                int cur = sa * nb + sb;
                for (Map.Entry<CharSet, Integer> ea : a.transitionsOf(sa).entrySet()) {
                    for (Map.Entry<CharSet, Integer> eb : b.transitionsOf(sb).entrySet()) {
                        CharSet inter = CharSet.intersect(ea.getKey(), eb.getKey());
                        if (!inter.isEmpty()) {
                            d.addTransition(cur, inter, ea.getValue() * nb + eb.getValue());
                        }
                    }
                }
                boolean accA = a.isAccept(sa);
                boolean accB = b.isAccept(sb);
                if (and ? (accA && accB) : (accA || accB)) {
                    d.addAccept(cur);
                }
            }
        }
        d.refineTransitions();
        return new Automaton(d);
    }

    /** 补全 DFA：无转移的字符 → 新的非接受 sink 状态（sink 自环）。 */
    private static Dfa complete(Dfa dfa) {
        Dfa out = new Dfa();
        for (int i = 0; i < dfa.stateCount(); i++) {
            out.newState();
        }
        int sink = out.newState();
        out.setStart(dfa.start());
        for (int s = 0; s < dfa.stateCount(); s++) {
            Map<CharSet, Integer> covered = new LinkedHashMap<>();
            for (Map.Entry<CharSet, Integer> e : dfa.transitionsOf(s).entrySet()) {
                out.addTransition(s, e.getKey(), e.getValue());
                covered.merge(e.getKey(), e.getValue(), (x, y) -> x);
            }
            // 未覆盖字符 → sink
            CharSet uncovered = CharSet.EMPTY;
            for (CharSet cs : covered.keySet()) {
                uncovered = CharSet.union(uncovered, cs);
            }
            CharSet rest = CharSet.complement(uncovered);
            if (!rest.isEmpty()) {
                out.addTransition(s, rest, sink);
            }
            if (dfa.isAccept(s)) {
                out.addAccept(s);
            }
        }
        // sink 自环，非接受
        out.addTransition(sink, CharSet.ALL, sink);
        return out;
    }
}
