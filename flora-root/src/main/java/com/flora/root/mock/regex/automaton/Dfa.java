package com.flora.root.mock.regex.automaton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 确定有限自动机（DFA），由 NFA 子集构造得到。
 * <p>状态以 int 编号；转移按互不相交的 {@link CharSet} 划分（确定性）。
 * 子集构造时先把同一子集上的字符集合切成原子区间，再对每个区间求全部可达目标的
 * ε-闭包——这样交替分支里重叠的字符区间（如 {@code [a-z]} 与 {@code [c-f]}）
 * 不会互相覆盖。</p>
 */
final class Dfa {

    /** 状态数上限：超出视为正则过于复杂（防指数爆炸）。 */
    private static final int MAX_STATES = 4096;

    private final List<Map<CharSet, Integer>> transitions = new ArrayList<>();
    private final Set<Integer> acceptStates = new LinkedHashSet<>();
    private int startState;

    int newState() {
        transitions.add(new LinkedHashMap<>());
        return transitions.size() - 1;
    }

    void addTransition(int from, CharSet cs, int to) {
        transitions.get(from).put(cs, to);
    }

    void setStart(int state) {
        this.startState = state;
    }

    void addAccept(int state) {
        acceptStates.add(state);
    }

    int start() {
        return startState;
    }

    int stateCount() {
        return transitions.size();
    }

    boolean isAccept(int state) {
        return acceptStates.contains(state);
    }

    Map<CharSet, Integer> transitionsOf(int state) {
        return transitions.get(state);
    }

    /** 从 NFA 子集构造 DFA。 */
    static Dfa fromNfa(Nfa nfa) {
        Dfa dfa = new Dfa();
        Map<Set<Integer>, Integer> stateIds = new LinkedHashMap<>();
        Deque<Set<Integer>> queue = new ArrayDeque<>();

        Set<Integer> start = epsilonClosure(nfa, Set.of(nfa.start()));
        stateIds.put(start, dfa.newState());
        queue.add(start);

        while (!queue.isEmpty()) {
            Set<Integer> cur = queue.poll();
            int curId = stateIds.get(cur);
            List<Move> moves = collectMoves(nfa, cur);
            // 每个原子区间 → 所有落在该区间内的转移目标的 ε-闭包
            for (int[] segment : atomicSegments(moves)) {
                Set<Integer> targets = new LinkedHashSet<>();
                for (Move move : moves) {
                    if (move.set.intersectsRange(segment[0], segment[1])) {
                        targets.add(move.target);
                    }
                }
                Set<Integer> next = epsilonClosure(nfa, targets);
                Integer nextId = stateIds.get(next);
                if (nextId == null) {
                    nextId = dfa.newState();
                    if (dfa.stateCount() > MAX_STATES) {
                        throw new AutomatonException("正则过于复杂，DFA 状态超上限 " + MAX_STATES);
                    }
                    stateIds.put(next, nextId);
                    queue.add(next);
                }
                dfa.addTransition(curId, CharSet.ofRange(segment[0], segment[1]), nextId);
            }
            for (int nState : cur) {
                if (nfa.isAccept(nState)) {
                    dfa.addAccept(curId);
                    break;
                }
            }
        }
        return dfa;
    }

    /** 收集 NFA 子集上的全部符号转移（字符集合可重叠，待切分）。 */
    private static List<Move> collectMoves(Nfa nfa, Set<Integer> states) {
        List<Move> moves = new ArrayList<>();
        for (int state : states) {
            for (Map.Entry<CharSet, List<Integer>> e : nfa.transitionsOf(state)) {
                for (int target : e.getValue()) {
                    moves.add(new Move(e.getKey(), target));
                }
            }
        }
        return moves;
    }

    /** 按全部区间的端点切出互不相交的原子区间。 */
    private static List<int[]> atomicSegments(List<Move> moves) {
        List<Integer> bounds = new ArrayList<>();
        for (Move move : moves) {
            int[] ranges = move.set.ranges();
            for (int i = 0; i < ranges.length; i += 2) {
                bounds.add(ranges[i]);
                bounds.add(ranges[i + 1] + 1);
            }
        }
        Collections.sort(bounds);
        List<int[]> segments = new ArrayList<>();
        for (int i = 0; i + 1 < bounds.size(); i++) {
            int lo = bounds.get(i);
            int hi = bounds.get(i + 1) - 1;
            if (lo <= hi) {
                segments.add(new int[]{lo, hi});
            }
        }
        return segments;
    }

    private static Set<Integer> epsilonClosure(Nfa nfa, Set<Integer> states) {
        Set<Integer> closure = new LinkedHashSet<>(states);
        Deque<Integer> stack = new ArrayDeque<>(states);
        while (!stack.isEmpty()) {
            int s = stack.pop();
            for (int next : nfa.epsilonOf(s)) {
                if (closure.add(next)) {
                    stack.push(next);
                }
            }
        }
        return closure;
    }

    /** 最小可达长度（到任一接受状态），不可达为 -1；有环路径允许任意长。 */
    int[] minLen() {
        int n = transitions.size();
        int[] dist = new int[n];
        java.util.Arrays.fill(dist, -1);
        Deque<Integer> queue = new ArrayDeque<>();
        for (int s : acceptStates) {
            dist[s] = 0;
            queue.add(s);
        }
        // 反向 BFS：需要反转移
        List<List<Integer>> reverse = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            reverse.add(new ArrayList<>());
        }
        for (int from = 0; from < n; from++) {
            for (int to : transitions.get(from).values()) {
                reverse.get(to).add(from);
            }
        }
        while (!queue.isEmpty()) {
            int s = queue.poll();
            for (int prev : reverse.get(s)) {
                if (dist[prev] < 0) {
                    dist[prev] = dist[s] + 1;
                    queue.add(prev);
                }
            }
        }
        return dist;
    }

    private record Move(CharSet set, int target) {
    }
}
