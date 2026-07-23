package com.flora.tangle.testbed.tricky;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lambda 表达式和方法引用测试类，验证混淆器对函数式编程结构的处理。
 * <p>
 * 包含多种函数式接口（Consumer, Function, Predicate, Supplier）、
 * Lambda 捕获局部变量、Lambda 捕获外部类字段、方法引用（静态方法、
 * 实例方法、构造器引用）、Stream API 链式操作等场景。
 * </p>
 */
public final class LambdaTest {

    /** 外部类字段，用于 Lambda 捕获测试 */
    private static final String PREFIX = "RES:";

    /** 实例字段，用于 Lambda 捕获测试 */
    private String instancePrefix = "INST:";

    /**
     * 静态工具方法 - 用于方法引用测试。
     *
     * @param s 输入字符串
     * @return 转换后的字符串
     */
    public static String transform(String s) {
        return "【" + s + "】";
    }

    /**
     * 实例方法 - 用于方法引用测试。
     *
     * @param x 输入
     * @return 加前缀后的结果
     */
    public String prefixIt(String x) {
        return instancePrefix + x;
    }

    /**
     * 构造器引用测试用的简单数据类。
     */
    public static class Person {
        private final String name;
        private final int age;

        /**
         * 构造 Person。
         *
         * @param name 姓名
         * @param age  年龄
         */
        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public int getAge() { return age; }

        @Override
        public String toString() {
            return name + "(" + age + ")";
        }
    }

    /**
     * 执行所有 Lambda 和 Stream 操作并返回结果摘要。
     *
     * @return 格式为 "LambdaTest:OK:xxx" 的结果字符串
     */
    public static String test() {
        LambdaTest instance = new LambdaTest();

        // 捕获的局部变量
        String localPrefix = "LOCAL:";
        int factor = 2;

        StringBuilder result = new StringBuilder();

        // ---- 1. Consumer + 捕获外部字段 + 捕获局部变量 ----
        List<String> consumed = new ArrayList<>();
        Consumer<String> consumer = s -> {
            // Lambda 捕获：静态字段 + 实例字段 + 局部变量
            String full = PREFIX + instance.instancePrefix + localPrefix + s;
            consumed.add(full);
        };
        consumer.accept("A");
        consumer.accept("B");
        consumer.accept("C");
        result.append("Consumer(").append(String.join(",", consumed)).append(")");

        // ---- 2. Function 接口 ----
        Function<String, Integer> strLen = String::length; // 实例方法引用
        Function<Integer, String> intStr = Object::toString; // 实例方法引用
        Function<String, String> pipeline = strLen.andThen(intStr);
        String pipeResult = pipeline.apply("LambdaTest");
        result.append(" | Function管道: ").append(pipeResult);

        // ---- 3. Predicate 接口 ----
        Predicate<String> isEmpty = String::isEmpty; // 实例方法引用
        Predicate<String> startsWithL = s -> s.startsWith("L");
        Predicate<String> complex = isEmpty.negate().and(startsWithL);
        boolean pred1 = complex.test("Lambda");
        boolean pred2 = complex.test("Alpha");
        boolean pred3 = complex.test("");
        result.append(" | Predicate: ").append(pred1).append(",")
                .append(pred2).append(",").append(pred3);

        // ---- 4. Supplier 接口 + 构造器引用 ----
        Supplier<StringBuilder> sbFactory = StringBuilder::new; // 构造器引用
        StringBuilder sb = sbFactory.get();
        sb.append("由Supplier创建");
        result.append(" | Supplier: ").append(sb.toString());

        // ---- 5. 静态方法引用 ----
        List<String> raw = Arrays.asList("hello", "world", "lambda", "java");
        List<String> transformed = raw.stream()
                .map(LambdaTest::transform) // 静态方法引用
                .collect(Collectors.toList());
        result.append(" | 静态方法引用: ").append(transformed);

        // ---- 6. 实例方法引用 ----
        List<String> prefixed = raw.stream()
                .map(instance::prefixIt) // 实例方法引用
                .collect(Collectors.toList());
        result.append(" | 实例方法引用: ").append(prefixed);

        // ---- 7. 构造器引用（Person 类） ----
        List<String> names = Arrays.asList("张三,25", "李四,30", "王五,28");
        List<Person> people = names.stream()
                .map(s -> s.split(","))
                .map(parts -> new Person(parts[0], Integer.parseInt(parts[1])))
                .collect(Collectors.toList());
        result.append(" | 构造器引用: ").append(people);

        // ---- 8. Stream API 链式操作 ----
        int streamResult = Stream.of(3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5)
                .filter(n -> n % 2 != 0)           // 奇数
                .map(n -> n * factor)                // 乘以捕获的局部变量
                .distinct()                          // 去重
                .sorted(Comparator.reverseOrder())   // 降序
                .peek(n -> { /* side-effect: 用于测试 Consumer 在 stream 中 */ })
                .limit(5)                            // 前 5 个
                .reduce(0, Integer::sum);            // 求和（方法引用）
        result.append(" | Stream链: ").append(streamResult);

        // ---- 9. Optional + Lambda ----
        Optional<String> maybe = Optional.of("LambdaOptional");
        String optResult = maybe
                .map(String::toUpperCase)
                .filter(s -> s.contains("LAMBDA"))
                .orElseGet(() -> "默认值");
        result.append(" | Optional: ").append(optResult);

        // ---- 10. BiFunction + Lambda ----
        BiFunction<Integer, Integer, String> biFn = (a, b) -> {
            int sum = a + b;
            int prod = a * b;
            return "sum=" + sum + ",prod=" + prod;
        };
        String biResult = biFn.apply(7, 11);
        result.append(" | BiFunction: ").append(biResult);

        // ---- 11. 自定义函数式接口 + Lambda ----
        TriFunction<String, Integer, Boolean, String> triFn =
                (s, n, flag) -> flag ? s.repeat(n) : s;
        String triResult = triFn.apply("Ha", 3, true);
        result.append(" | TriFunction: ").append(triResult);

        // ---- 12. Stream 分组和归约 ----
        Map<Integer, List<Person>> groupedByAge = people.stream()
                .collect(Collectors.groupingBy(Person::getAge));
        result.append(" | 分组: ").append(groupedByAge.size()).append("组");

        // ---- 13. 并行 Stream ----
        long parallelCount = Stream.of(10, 20, 30, 40, 50)
                .parallel()
                .map(n -> n * 2)
                .filter(n -> n > 50)
                .count();
        result.append(" | 并行: ").append(parallelCount);

        return "LambdaTest:OK:" + result.toString();
    }

    /**
     * 三参数函数式接口（用于验证混淆器对非标准函数式接口的处理）。
     *
     * @param <T> 第一个参数类型
     * @param <U> 第二个参数类型
     * @param <V> 第三个参数类型
     * @param <R> 返回类型
     */
    @FunctionalInterface
    public interface TriFunction<T, U, V, R> {
        /**
         * 应用此函数。
         *
         * @param t 第一个参数
         * @param u 第二个参数
         * @param v 第三个参数
         * @return 函数结果
         */
        R apply(T t, U u, V v);
    }
}
