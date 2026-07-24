package com.flora.java.clazz;

/**
 * 继承距离计算器，计算两个类/接口之间的继承层次距离。
 * <p>用于方法重载解析优先级判定。距离越小表示类型关系越近。</p>
 * <p>距离即「从 descendant 到 ancestor 在继承图中的最短路径边数」，由
 * {@link InheritTreeTraverser} 的 BFS 统一计算；本类只补一个 traverser 不掌握的
 * 领域特例：接口在名义类型体系中无 Object 超类，但运行期接口值都是 Object 实例，
 * 故接口到 Object 的距离固定为 1。</p>
 */
public final class InheritDistanceCalculator {
    /** 最大距离（相当于不可达），用于表示无继承关系 */
    public static final int MAX_DISTANCE = Integer.MAX_VALUE / 2;

    private InheritDistanceCalculator() {
    }

    /**
     * 计算指定类到 Object 的继承距离。
     *
     * @param descendant 子类
     * @return 继承距离
     */
    public static int inheritDistance(Class<?> descendant) {
        return inheritDistance(descendant, Object.class);
    }

    /**
     * 计算 descendant 到 ancestor 的继承距离。
     * <p>同时处理类到类、类到接口、接口到接口的继承距离计算。</p>
     *
     * @param descendant 子类/子接口
     * @param ancestor   父类/父接口
     * @return 继承距离，不可达返回 {@link #MAX_DISTANCE}
     */
    public static int inheritDistance(Class<?> descendant, Class<?> ancestor) {
        if (ancestor == null || descendant == null) {
            return MAX_DISTANCE;
        }
        if (descendant.equals(ancestor)) {
            return 0;
        }
        // 接口在名义类型体系中无 Object 超类，但运行期接口值都是 Object 实例，
        // 故接口到 Object 距离固定为 1；其余「接口→类」均不可达，返回 MAX_DISTANCE
        if (descendant.isInterface() && !ancestor.isInterface()
                && ancestor.equals(Object.class)) {
            return 1;
        }
        // 其余情况交给继承树 BFS：level 即最短路径边数
        // （BFS 保证每个类型首次被访问时经过的即是最短路径）
        int[] found = {MAX_DISTANCE};
        InheritTreeTraverser.traverse(descendant, (type, level) -> {
            if (type.equals(ancestor)) {
                found[0] = level;
            }
        });
        return found[0];
    }
}
