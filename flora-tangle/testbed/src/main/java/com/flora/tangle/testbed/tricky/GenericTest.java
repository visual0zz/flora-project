package com.flora.tangle.testbed.tricky;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 泛型和类型擦除边界情况测试类。
 * <p>
 * 包含泛型类 Box&lt;T&gt;、泛型方法、通配符 ? extends / ? super、
 * 类型擦除后的桥方法等场景，确保混淆器正确保留泛型签名信息。
 * </p>
 */
public final class GenericTest {

    /**
     * 泛型容器类，模拟一个简单的装箱操作。
     *
     * @param <T> 容器内元素的类型
     */
    public static class Box<T> {
        private T value;
        private final Class<T> type;

        /**
         * 构造一个泛型 Box。
         *
         * @param type 类型的 Class 对象（克服类型擦除）
         */
        public Box(Class<T> type) {
            this.type = type;
        }

        /**
         * 设置值。
         *
         * @param value 要存储的值
         */
        public void set(T value) {
            this.value = value;
        }

        /**
         * 获取值。
         *
         * @return 存储的值
         */
        public T get() {
            return value;
        }

        /**
         * 获取类型名称。
         *
         * @return 类型简单名称
         */
        public String typeName() {
            return type.getSimpleName();
        }

        /**
         * 判断值是否为指定类型。
         *
         * @return 如果值类型匹配返回 true
         */
        public boolean isTypeMatch() {
            return value != null && type.isAssignableFrom(value.getClass());
        }

        @Override
        public String toString() {
            return "Box<" + typeName() + ">:" + value;
        }
    }

    /**
     * 数字盒子，继承 Box，有类型边界。
     *
     * @param <N> 必须是 Number 或其子类
     */
    public static class NumberBox<N extends Number> extends Box<N> {

        public NumberBox(Class<N> type) {
            super(type);
        }

        /**
         * 将值转换为 double。
         *
         * @return double 值
         */
        public double toDouble() {
            N v = get();
            if (v == null) {
                return 0.0;
            }
            return v.doubleValue();
        }

        /**
         * 比较两个 NumberBox 的值大小（桥方法测试）。
         *
         * @param other 另一个 NumberBox
         * @return 比较结果
         */
        public int compareTo(NumberBox<? extends Number> other) {
            return Double.compare(this.toDouble(), other.toDouble());
        }
    }

    /**
     * 泛型方法：计算列表元素数量。
     *
     * @param list 任意类型的列表
     * @param <T>  元素类型
     * @return 元素数量
     */
    public static <T> int sizeOf(List<T> list) {
        return list.size();
    }

    /**
     * 泛型方法：从数组创建列表（模拟 Arrays.asList）。
     *
     * @param array 输入数组
     * @param <T>   元素类型
     * @return 列表
     */
    @SafeVarargs
    public static <T> List<T> asList(T... array) {
        List<T> result = new ArrayList<>();
        for (T item : array) {
            result.add(item);
        }
        return result;
    }

    /**
     * 通配符 ? extends: 从列表中读取 Number。
     *
     * @param list 元素为 Number 子类的列表
     * @return 列表元素的和
     */
    public static double sumOf(List<? extends Number> list) {
        double sum = 0.0;
        for (Number n : list) {
            sum += n.doubleValue();
        }
        return sum;
    }

    /**
     * 通配符 ? super: 向列表中写入 Integer。
     *
     * @param list 元素为 Integer 或其父类的列表
     * @param values 要添加的值
     */
    @SafeVarargs
    public static void addAll(List<? super Integer> list, Integer... values) {
        for (Integer v : values) {
            list.add(v);
        }
    }

    /**
     * 泛型接口，用于测试桥方法和类型擦除。
     *
     * @param <T> 比较类型
     */
    public interface Comparable<T> {
        int compareTo(T other);
    }

    /**
     * 实现泛型接口的具体类，验证桥方法生成。
     */
    public static class StringWrapper implements Comparable<String> {
        private final String value;

        public StringWrapper(String value) {
            this.value = value;
        }

        @Override
        public int compareTo(String other) {
            return this.value.compareTo(other);
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 执行所有泛型操作并返回结果摘要。
     *
     * @return 格式为 "GenericTest:OK:xxx" 的结果字符串
     */
    public static String test() {
        StringBuilder result = new StringBuilder();

        // ---- 1. 泛型类 Box 的基本使用 ----
        Box<String> strBox = new Box<>(String.class);
        strBox.set("Hello Generic");
        Box<Integer> intBox = new Box<>(Integer.class);
        intBox.set(42);

        result.append("strBox=").append(strBox.get())
                .append("(").append(strBox.typeName()).append(")")
                .append(", intBox=").append(intBox.get())
                .append("(").append(intBox.typeName()).append(")")
                .append(", isMatch=").append(strBox.isTypeMatch());

        // ---- 2. 有界类型参数的 NumberBox ----
        NumberBox<Integer> numBox = new NumberBox<>(Integer.class);
        numBox.set(100);
        NumberBox<Double> dblBox = new NumberBox<>(Double.class);
        dblBox.set(50.5);

        int compareResult = numBox.compareTo(dblBox);
        result.append(" | NumberBox: ").append(numBox.toDouble())
                .append(" vs ").append(dblBox.toDouble())
                .append(" = ").append(compareResult);

        // ---- 3. 泛型方法调用 ----
        List<String> strList = asList("A", "B", "C", "D", "E");
        int listSize = sizeOf(strList);
        result.append(" | 泛型方法:size=").append(listSize);

        // ---- 4. ? extends 通配符 ----
        List<Integer> intList = Arrays.asList(10, 20, 30, 40, 50);
        double extSum = sumOf(intList);
        result.append(" | extends通配:sum=").append(extSum);

        // ---- 5. ? super 通配符 ----
        List<Number> numList = new ArrayList<>();
        numList.add(1.5);
        numList.add(2.5);
        addAll(numList, 10, 20, 30);
        double supSum = sumOf(numList);
        result.append(" | super通配:sum=").append(supSum);

        // ---- 6. 桥方法测试 ----
        StringWrapper wrapper = new StringWrapper("Hello");
        int cmp = wrapper.compareTo("Hello");
        result.append(" | 桥方法:compareTo=").append(cmp);

        // ---- 7. 泛型与函数式接口结合 ----
        Function<String, Integer> parser = Integer::parseInt;
        Integer parsed = parser.apply("123");
        result.append(" | 泛型+函数式:").append(parsed);

        // ---- 8. 通配符捕获辅助 ----
        List<?> wildList = Arrays.asList("捕获", "测试", "Wildcard");
        int wildSize = captureHelper(wildList);
        result.append(" | 通配符捕获:size=").append(wildSize);

        // ---- 9. 排序（使用泛型 Comparator） ----
        List<Integer> sortList = Arrays.asList(3, 1, 4, 1, 5, 9, 2, 6);
        sortList.sort(Comparator.naturalOrder());
        result.append(" | 泛型排序:").append(sortList);

        return "GenericTest:OK:" + result.toString();
    }

    /**
     * 通配符捕获辅助方法。
     *
     * @param list 任意类型的列表
     * @param <T>  捕获的类型
     * @return 列表大小
     */
    private static <T> int captureHelper(List<T> list) {
        // 在方法内部 T 被捕获为一个具体类型
        List<T> copy = new ArrayList<>(list);
        // 反转后添加
        for (int i = list.size() - 1; i >= 0; i--) {
            copy.add(list.get(i));
        }
        return copy.size();
    }
}
