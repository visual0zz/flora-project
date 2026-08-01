package com.flora.mock.automaton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 非确定有限自动机（NFA），Thompson 构造产物。
 * <p>状态以 int 编号；转移分为两类：带字符集合标签的符号转移，
 * 以及 ε 转移（空转移，用于构造组合）。acceptStates 标记接受状态。</p>
 */
public final class Nfa {

    private final List<Map<CharSet, List<Integer>>> transitions = new ArrayList<>();
    private final List<List<Integer>> epsilon = new ArrayList<>();
    private final java.util.Set<Integer> acceptStates = new java.util.LinkedHashSet<>();
    private int startState;

    /** 创建新状态，返回其编号。 */
    int newState() {
        transitions.add(new HashMap<>());
        epsilon.add(new ArrayList<>());
        return transitions.size() - 1;
    }

    /** 添加符号转移：from --charSet--> to。 */
    void addTransition(int from, CharSet charSet, int to) {
        transitions.get(from).computeIfAbsent(charSet, k -> new ArrayList<>()).add(to);
    }

    /** 添加 ε 转移：from --ε--> to。 */
    void addEpsilon(int from, int to) {
        epsilon.get(from).add(to);
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

    List<Map.Entry<CharSet, List<Integer>>> transitionsOf(int state) {
        return new ArrayList<>(transitions.get(state).entrySet());
    }

    List<Integer> epsilonOf(int state) {
        return epsilon.get(state);
    }

    boolean isAccept(int state) {
        return acceptStates.contains(state);
    }
}
