package com.flora.java;

import com.flora.java.clazz.InheritDistanceCalculator;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InheritDistanceCalculator.inheritDistance 的完整单测。
 * 覆盖 4 种情况：类→类、类→接口、接口→接口、接口→类。
 */
class InheritDistanceCalculatorTest {

    // ========== null 守卫 ==========

    /**
     * 测试 null 后代返回 MAX_DISTANCE。
     */
    @Test
    void nullDescendant() {
        assertEquals(InheritDistanceCalculator.MAX_DISTANCE,
                InheritDistanceCalculator.inheritDistance(null, Object.class));
    }

    /**
     * 测试 null 祖先返回 MAX_DISTANCE。
     */
    @Test
    void nullAncestor() {
        assertEquals(InheritDistanceCalculator.MAX_DISTANCE,
                InheritDistanceCalculator.inheritDistance(String.class, null));
    }

    /**
     * 测试双方 null 返回 MAX_DISTANCE。
     */
    @Test
    void bothNull() {
        assertEquals(InheritDistanceCalculator.MAX_DISTANCE,
                InheritDistanceCalculator.inheritDistance(null, null));
    }

    // ========== 自身 ==========

    /**
     * 测试相同类型继承距离为 0。
     */
    @Test
    void sameTypeReturnsZero() {
        assertEquals(0, InheritDistanceCalculator.inheritDistance(Integer.class, Integer.class));
        assertEquals(0, InheritDistanceCalculator.inheritDistance(String.class, String.class));
        assertEquals(0, InheritDistanceCalculator.inheritDistance(List.class, List.class));
        assertEquals(0, InheritDistanceCalculator.inheritDistance(int[].class, int[].class));
    }

    // ========== 类 → 类（case 4） ==========

    /**
     * 测试直接父类继承距离为 1。
     */
    @Test
    void directParent() {
        assertEquals(1, InheritDistanceCalculator.inheritDistance(Integer.class, Number.class));
        assertEquals(1, InheritDistanceCalculator.inheritDistance(ArrayList.class, AbstractList.class));
        assertEquals(1, InheritDistanceCalculator.inheritDistance(TreeSet.class, AbstractSet.class));
    }

    /**
     * 测试间接父类继承距离。
     */
    @Test
    void indirectParent() {
        assertEquals(2, InheritDistanceCalculator.inheritDistance(Integer.class, Object.class));
        assertEquals(2, InheritDistanceCalculator.inheritDistance(Vector.class, AbstractCollection.class));
        assertEquals(2, InheritDistanceCalculator.inheritDistance(HashMap.class, Object.class));
    }

    /**
     * 测试无关类返回 MAX_DISTANCE。
     */
    @Test
    void unrelatedClasses() {
        assertEquals(InheritDistanceCalculator.MAX_DISTANCE,
                InheritDistanceCalculator.inheritDistance(Integer.class, String.class));
        assertEquals(InheritDistanceCalculator.MAX_DISTANCE,
                InheritDistanceCalculator.inheritDistance(String.class, Integer.class));
    }

    /**
     * 测试深度类层次结构的继承距离。
     */
    @Test
    void deepHierarchy() {
        assertEquals(3, InheritDistanceCalculator.inheritDistance(TreeSet.class, Object.class));
        assertEquals(2, InheritDistanceCalculator.inheritDistance(HashMap.class, Object.class));
    }

    // ========== 类 → 接口（case 3） ==========

    /**
     * 测试类直接实现接口的继承距离为 1。
     */
    @Test
    void directInterface() {
        assertEquals(1, InheritDistanceCalculator.inheritDistance(ArrayList.class, List.class));
        assertEquals(1, InheritDistanceCalculator.inheritDistance(HashMap.class, Map.class));
        assertEquals(1, InheritDistanceCalculator.inheritDistance(TreeSet.class, NavigableSet.class));
    }

    /**
     * 测试类通过父类间接实现接口的继承距离。
     */
    @Test
    void interfaceThroughParent() {
        assertEquals(3, InheritDistanceCalculator.inheritDistance(ArrayList.class, Collection.class));
        assertEquals(1, InheritDistanceCalculator.inheritDistance(HashMap.class, Map.class));
    }

    /**
     * 测试类通过父类实现接口。
     */
    @Test
    void interfaceThroughClassParent() {
        assertEquals(2, InheritDistanceCalculator.inheritDistance(LinkedHashMap.class, Map.class));
    }

    /**
     * 测试类实现多个接口的继承距离。
     */
    @Test
    void multipleInterfaces() {
        assertEquals(1, InheritDistanceCalculator.inheritDistance(ArrayList.class, RandomAccess.class));
        assertEquals(1, InheritDistanceCalculator.inheritDistance(ArrayList.class, Cloneable.class));
        assertEquals(1, InheritDistanceCalculator.inheritDistance(ArrayList.class, Serializable.class));
    }

    /**
     * 测试类未实现接口时返回 MAX_DISTANCE。
     */
    @Test
    void classNotImplementingInterface() {
        assertEquals(InheritDistanceCalculator.MAX_DISTANCE,
                InheritDistanceCalculator.inheritDistance(String.class, List.class));
    }

    // ========== 接口 → 接口（case 1） ==========

    /**
     * 测试接口到父接口的继承距离。
     */
    @Test
    void interfaceToSuperInterface() {
        assertEquals(2, InheritDistanceCalculator.inheritDistance(List.class, Collection.class));
        assertEquals(1, InheritDistanceCalculator.inheritDistance(Collection.class, Iterable.class));
        assertEquals(1, InheritDistanceCalculator.inheritDistance(NavigableSet.class, SortedSet.class));
    }

    /**
     * 测试接口到间接父接口的继承距离。
     */
    @Test
    void interfaceToIndirectSuperInterface() {
        assertEquals(3, InheritDistanceCalculator.inheritDistance(List.class, Iterable.class));
    }

    /**
     * 测试无关接口返回 MAX_DISTANCE。
     */
    @Test
    void unrelatedInterfaces() {
        assertEquals(InheritDistanceCalculator.MAX_DISTANCE,
                InheritDistanceCalculator.inheritDistance(List.class, Map.class));
    }

    // ========== 接口 → 类（case 2） ==========

    /**
     * 测试接口到 Object 的继承距离为 1。
     */
    @Test
    void interfaceToObject() {
        assertEquals(1, InheritDistanceCalculator.inheritDistance(List.class, Object.class));
    }

    /**
     * 测试接口到非 Object 类返回 MAX_DISTANCE。
     */
    @Test
    void interfaceToNonObjectClass() {
        assertEquals(InheritDistanceCalculator.MAX_DISTANCE,
                InheritDistanceCalculator.inheritDistance(List.class, String.class));
        assertEquals(InheritDistanceCalculator.MAX_DISTANCE,
                InheritDistanceCalculator.inheritDistance(Collection.class, ArrayList.class));
    }

    // ========== Enum / Annotation 特殊类型 ==========

    /**
     * 测试枚举到父类的继承距离。
     */
    @Test
    void enumToParent() {
        assertEquals(1, InheritDistanceCalculator.inheritDistance(Thread.State.class, Enum.class));
    }

    /**
     * 测试注解到父接口的继承距离。
     */
    @Test
    void annotationToParent() {
        assertEquals(1, InheritDistanceCalculator.inheritDistance(Override.class, Annotation.class));
    }

    // ========== Object 作为 descendant ==========

    /**
     * 测试 Object 到自身的距离为 0。
     */
    @Test
    void objectToObject() {
        assertEquals(0, InheritDistanceCalculator.inheritDistance(Object.class, Object.class));
    }

    /**
     * 测试 Object 未实现接口返回 MAX_DISTANCE。
     */
    @Test
    void objectDoesNotImplementInterface() {
        assertEquals(InheritDistanceCalculator.MAX_DISTANCE,
                InheritDistanceCalculator.inheritDistance(Object.class, Serializable.class));
    }

    /**
     * 测试 Object 作为后代到子类返回 MAX_DISTANCE。
     */
    @Test
    void objectToSubclass() {
        assertEquals(InheritDistanceCalculator.MAX_DISTANCE,
                InheritDistanceCalculator.inheritDistance(Object.class, String.class));
    }

    // ========== 基本类型（非 Object） ==========

    /**
     * 测试基本类型到 Object 返回 MAX_DISTANCE。
     */
    @Test
    void primitiveToObject() {
        assertEquals(InheritDistanceCalculator.MAX_DISTANCE,
                InheritDistanceCalculator.inheritDistance(int.class, Object.class));
    }

    // ========== 父类路径比直接接口链更短 ==========

    /**
     * 验证父类路径比直接接口链更短时选取父类路径。
     */
    @Test
    void interfaceViaParentShorterThanDirectChain() {
        assertEquals(2, InheritDistanceCalculator.inheritDistance(TreeMap.class, Map.class));
    }

    // ========== 单参数重载 ==========

    /**
     * 测试单参数重载默认祖先为 Object。
     */
    @Test
    void singleArgDefaultsToObject() {
        assertEquals(2, InheritDistanceCalculator.inheritDistance(Integer.class));
        assertEquals(1, InheritDistanceCalculator.inheritDistance(List.class));
    }

    // ========== 数组类型 ==========

    /**
     * 测试基本类型数组到 Object。
     */
    @Test
    void primitiveArrayToObject() {
        assertEquals(1, InheritDistanceCalculator.inheritDistance(int[].class, Object.class));
    }

    /**
     * 测试基本类型数组到接口。
     */
    @Test
    void primitiveArrayToInterface() {
        assertEquals(1, InheritDistanceCalculator.inheritDistance(int[].class, Cloneable.class));
        assertEquals(1, InheritDistanceCalculator.inheritDistance(int[].class, Serializable.class));
    }

    // ========== MAX_DISTANCE 常量 ==========

    /**
     * 测试 MAX_DISTANCE 常量的值。
     */
    @Test
    void maxDistanceValue() {
        assertEquals(Integer.MAX_VALUE / 2, InheritDistanceCalculator.MAX_DISTANCE);
    }
}
