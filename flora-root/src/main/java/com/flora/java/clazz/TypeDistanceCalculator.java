package com.flora.java.clazz;

/**
 * 类型继承距离计算器，计算两个类/接口之间的继承层次距离。
 * <p>用于方法重载解析优先级判定。距离越小表示类型关系越近。</p>
 */
public final class TypeDistanceCalculator {
    /** 最大距离（相当于不可达），用于表示无继承关系 */
    public static final int MAX_DISTANCE = Integer.MAX_VALUE/2;

    private TypeDistanceCalculator() {
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

        boolean descIsInterface = descendant.isInterface();
        boolean ancIsInterface  = ancestor.isInterface();

        if (descIsInterface) {
            if (ancIsInterface) {
                return interfaceChainDistance(descendant, ancestor);
            }else{
                return ancestor.equals(Object.class) ? 1 : MAX_DISTANCE;
            }
        }else{
            if (ancIsInterface) {
                int min =interfaceChainDistance(descendant, ancestor);
                Class<?> parent = descendant.getSuperclass();
                if (parent != null) {
                    min = Math.min(min, inheritDistance(parent, ancestor) + 1);
                }
                return min;
            }else{
                int distance = 0;
                while (descendant != null) {
                    if (descendant.equals(ancestor)) {
                        return distance;
                    }
                    distance++;
                    descendant = descendant.getSuperclass();
                }
                return MAX_DISTANCE;
            }
        }

    }

    /**
     * 计算接口链中 descendant 到 ancestor 的距离。
     *
     * @param descendant 子接口
     * @param ancestor   父接口
     * @return 接口链距离
     */
    private static int interfaceChainDistance(Class<?> descendant, Class<?> ancestor) {
        int min = MAX_DISTANCE;
        for (Class<?> face : descendant.getInterfaces()) {
            if (face.equals(ancestor)) {
                return 1;
            }
            min = Math.min(min, interfaceChainDistance(face, ancestor) + 1);
        }
        return min;
    }

}
