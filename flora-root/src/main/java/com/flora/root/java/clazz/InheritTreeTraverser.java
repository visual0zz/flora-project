package com.flora.root.java.clazz;

import java.util.*;

/**
 * 遍历某个类或接口的完整继承树（超类与接口，传递闭包），对每个类型调用一次访问器。
 * 传给访问器的 level 是从根类型到该类型的最短路径（边数），通过 BFS 计算得出。
 * <p>
 * 访问器返回 {@link TraversalAction#CONTINUE}（或 {@code null}）继续遍历该类型的父类型，
 * {@link TraversalAction#PRUNE} 跳过该类型的父类型（不影响队列中其他类型），
 * {@link TraversalAction#TERMINATE} 立即终止整个遍历。
 * </p>
 */
public final class InheritTreeTraverser {

    /**
     * 以 {@code root} 为根遍历继承树，对遇到的每个类或接口调用一次访问器。
     * 根类型本身报告为 level 0；每个直接超类或接口为 level 1；沿最短路径依次递增。
     *
     * @param root    被遍历继承树的类或接口；可以为 {@code null}（此时不执行任何操作）
     * @param visitor 访问器，{@code null} 返回值等效于 {@link TraversalAction#CONTINUE}
     */
    public static void traverse(Class<?> root, InheritVisitor visitor) {
        if (root == null) {
            return;
        }
        Set<Class<?>> visited = new HashSet<>();
        Queue<Node> queue = new ArrayDeque<>();
        queue.add(new Node(root, 0));
        visited.add(root);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            TraversalAction action = visitor.visit(current.type, current.level);
            if (action == TraversalAction.TERMINATE) {
                return;
            }
            if (action == TraversalAction.PRUNE) {
                continue;
            }
            // null 或 CONTINUE：继续遍历父类型
            for (Class<?> parent : parentsOf(current.type)) {
                if (visited.add(parent)) {
                    queue.add(new Node(parent, current.level + 1));
                }
            }
        }
    }

    private static List<Class<?>> parentsOf(Class<?> type) {
        List<Class<?>> parents = new ArrayList<>();
        Class<?> superClass = type.getSuperclass();
        if (superClass != null) {
            parents.add(superClass);
        }
        parents.addAll(Arrays.asList(type.getInterfaces()));
        return parents;
    }

    private record Node(Class<?> type, int level) {
    }

    /**
     * 遍历继承树时为每个类型调用一次的访问器。
     * <p>
     * 返回 {@code null} 等效于 {@link TraversalAction#CONTINUE}。
     *
     * @return {@link TraversalAction#CONTINUE} 或 {@code null} 继续遍历此类型的父类型；
     *         {@link TraversalAction#PRUNE} 跳过此类型的父类型；
     *         {@link TraversalAction#TERMINATE} 终止整个遍历
     */
    @FunctionalInterface
    public interface InheritVisitor {
        TraversalAction visit(Class<?> type, int level);
    }

    /** 遍历行为控制。 */
    public enum TraversalAction {
        /** 继续遍历此类型的父类型 */
        CONTINUE,
        /** 跳过此类型的父类型，但不影响队列中其他类型 */
        PRUNE,
        /** 立即停止整个遍历 */
        TERMINATE
    }
}
