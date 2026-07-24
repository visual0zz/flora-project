package com.flora.java.clazz;

import java.util.*;


/**
 * 遍历某个类或接口的完整继承树（超类与接口，传递闭包），对每个类型调用一次访问器。
 * 传给访问器的 level 是从根类型到该类型的最短路径（边数），通过 BFS 计算得出。
 */
public final class InheritTreeTraverser {

    /**
     * 以 {@code root} 为根遍历继承树，对遇到的每个类或接口调用一次访问器（构造时提供）。
     * 根类型本身报告为 level 0；每个直接超类或接口为 level 1；沿最短路径依次递增。
     *
     * @param root 被遍历继承树的类或接口；可以为 {@code null}（此时不执行任何操作）
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
            visitor.visit(current.type, current.level);
            for (Class<?> parent : parentsOf(current.type)) {
                if (visited.add(parent)) {
                    queue.add(new Node(parent, current.level + 1));
                }
            }
        }
    }

    /**
     * 某个类型的直接父类型：其超类（若有）在前，随后是所有直接声明的接口。
     * 接口继承也被覆盖，因为接口自身的超接口由 {@link Class#getInterfaces()} 返回。
     */
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
     * 遍历继承树时为每个类型调用一次的访问器。{@code level} 是从根类型到该类型的最短路径（边数）。
     */
    @FunctionalInterface
    public interface InheritVisitor {
        void visit(Class<?> type, int level);
    }
}
