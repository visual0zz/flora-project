package com.flora.mock.regex.automaton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 确定有限自动机（DFA），由 NFA 子集构造得到。
 * <p>状态以 int 编号；转移为 CharSet → 目标状态（确定性）。
 * 提供匹配、采样所需的可达性长度预处理。</p>
 */
final class Dfa {

    private final List<Map<CharSet, Integer>> transitions = new ArrayList<>();
    private final Set<Integer> acceptStates = new HashSet<>();
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
        Map<Set<Integer>, Integer> stateIds = new HashMap<>();
        Deque<Set<Integer>> queue = new ArrayDeque<>();

        Set<Integer> start = epsilonClosure(nfa, Set.of(nfa.start()));
        stateIds.put(start, 0);
        dfa.newState();
        queue.add(start);

        while (!queue.isEmpty()) {
            Set<Integer> cur = queue.poll();
            int curId = stateIds.get(cur);
            // 收集该 NFA 子集上所有符号转移，按 CharSet 分组
            Map<CharSet, Set<Integer>> moves = new LinkedHashMap<>();
            for (int nState : cur) {
                for (Map.Entry<CharSet, List<Integer>> e : nfa.transitionsOf(nState)) {
                    CharSet cs = e.getKey();
                    for (int to : e.getValue()) {
                        Set<Integer> target = moves.computeIfAbsent(cs, k -> new HashSet<>());
                        target.add(to);
                    }
                }
            }
            // 合并边界重叠的 CharSet（简单起见：两两合并相邻可合并项）
            for (Map.Entry<CharSet, Set<Integer>> e : moves.entrySet()) {
                Set<Integer> targetSet = epsilonClosure(nfa, e.getValue());
                Integer targetId = stateIds.get(targetSet);
                if (targetId == null) {
                    targetId = dfa.newState();
                    stateIds.put(targetSet, targetId);
                    queue.add(targetSet);
                }
                dfa.addTransition(curId, e.getKey(), targetId);
            }
            // 接受状态：子集包含 NFA 接受状态
            for (int nState : cur) {
                if (nfa.isAccept(nState)) {
                    dfa.addAccept(curId);
                    break;
                }
            }
        }
        dfa.refineTransitions();
        return dfa;
    }

    /** 细化转移：把重叠的 CharSet 拆分为互不相交的原子区间，保证确定性。 */
    void refineTransitions() {
        for (int s = 0; s < transitions.size(); s++) {
            Map<CharSet, Integer> out = transitions.get(s);
            if (out.size() <= 1) {
                continue;
            }
            // 收集所有边界点，切分互斥区间
            List<Integer> bounds = new ArrayList<>();
            for (CharSet cs : out.keySet()) {
                int[] r = cs.ranges();
                for (int i = 0; i < r.length; i += 2) {
                    bounds.add(r[i]);
                    bounds.add(r[i + 1] + 1);
                }
            }
            bounds.sort(Integer::compareTo);
            Map<CharSet, Integer> refined = new LinkedHashMap<>();
            for (int i = 0; i + 1 < bounds.size(); i++) {
                int lo = bounds.get(i);
                int hi = bounds.get(i + 1) - 1;
                if (lo > hi) {
                    continue;
                }
                CharSet seg = CharSet.ofRange(lo, hi);
                Integer target = null;
                for (Map.Entry<CharSet, Integer> e : out.entrySet()) {
                    if (!CharSet.intersect(e.getKey(), seg).isEmpty()) {
                        target = e.getValue();
                        break;
                    }
                }
                if (target != null) {
                    refined.merge(seg, target, (a, b) -> a);
                }
            }
            transitions.set(s, refined);
        }
    }

    private static Set<Integer> epsilonClosure(Nfa nfa, Set<Integer> states) {
        Set<Integer> closure = new HashSet<>(states);
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
}
