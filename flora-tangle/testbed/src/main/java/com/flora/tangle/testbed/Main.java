package com.flora.tangle.testbed;

import java.lang.reflect.Method;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * testbed 主入口 — 通过反射加载代码并持续验证混淆正确性。
 *
 * <p>功能：通过 {@code Class.forName()} 反射加载各个测试类，调用其测试方法，
 * 每步打印时间戳和序号，模拟真实应用中的反射调用场景。
 * 混淆器必须正确处理被反射引用的类和方法。
 */
public final class Main {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Main() {}

    /**
     * 入口方法。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        System.out.println("=== testbed 启动 ===");

        int seq = 0;

        // 使用反射逐个加载并执行测试
        String[] testClasses = {
            "com.flora.tangle.testbed.tricky.StringTest",
            "com.flora.tangle.testbed.tricky.LoopTest",
            "com.flora.tangle.testbed.tricky.ExceptionTest",
            "com.flora.tangle.testbed.tricky.ReflectionTest",
            "com.flora.tangle.testbed.tricky.GenericTest",
            "com.flora.tangle.testbed.tricky.EnumTest",
            "com.flora.tangle.testbed.tricky.InnerClassTest",
            "com.flora.tangle.testbed.tricky.RecursionTest",
            "com.flora.tangle.testbed.tricky.LambdaTest",
        };

        for (String className : testClasses) {
            try {
                seq++;
                String now = LocalTime.now().format(TIME_FMT);
                System.out.println("[" + seq + "] " + now + " 加载: " + className);

                // 反射加载类
                Class<?> clazz = Class.forName(className);
                // 反射调用无参的 test() 方法
                Method testMethod = clazz.getMethod("test");
                Object result = testMethod.invoke(null);
                System.out.println("[" + seq + "] 结果: " + result);
            } catch (Exception e) {
                System.err.println("[" + seq + "] 错误: " + className
                        + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }

        // 综合验证
        seq++;
        System.out.println("[" + seq + "] " + LocalTime.now().format(TIME_FMT) + " 全部完成");
        System.out.println("=== testbed 结束 ===");
    }
}
