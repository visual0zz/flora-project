package com.flora.tangle.testbed.tricky;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 自我反射调用测试类，验证混淆器处理反射引用的正确性。
 * <p>
 * 通过 Class.forName() 加载自身类，通过 Method.invoke() 调用私有方法，
 * 通过 Field.set/get 访问私有字段，同时包含多态场景下的反射调用，
 * 确保混淆不会破坏反射依赖。
 * </p>
 */
public final class ReflectionTest {

    /** 私有静态字段 - 用于反射测试 */
    private static String privateStaticField = "初始静态值";

    /** 私有实例字段 - 用于反射测试 */
    private String privateInstanceField;

    /** 受保护字段 */
    protected String protectedField = "受保护字段值";

    /** 包级可见字段 */
    String packageField = "包级字段值";

    /**
     * 构造器，初始化实例字段。
     */
    public ReflectionTest() {
        this.privateInstanceField = "初始实例值";
    }

    /**
     * 私有静态方法 - 用于反射调用测试。
     *
     * @param name 名称参数
     * @return 拼接结果
     */
    @SuppressWarnings("unused")
    private static String privateStaticMethod(String name) {
        return "私有静态方法被调用, 参数=" + name;
    }

    /**
     * 私有实例方法 - 用于反射调用测试。
     *
     * @param x 第一个整数
     * @param y 第二个整数
     * @return 计算结果
     */
    @SuppressWarnings("unused")
    private int privateInstanceMethod(int x, int y) {
        return x * y + x - y;
    }

    /**
     * 受保护方法 - 用于反射调用测试。
     *
     * @param msg 消息
     * @return 处理后的消息
     */
    @SuppressWarnings("unused")
    protected String protectedMethod(String msg) {
        return "【受保护】" + msg;
    }

    /**
     * 包级可见方法 - 用于反射调用测试。
     *
     * @param items 数组
     * @return 数组元素拼接结果
     */
    @SuppressWarnings("unused")
    String packageMethod(String[] items) {
        return String.join(",", items);
    }

    /**
     * 内部辅助类，用于反射测试多态场景。
     */
    public static class Helper {
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    /**
     * 通过反射执行全部操作并返回摘要。
     *
     * @return 格式为 "ReflectionTest:OK:xxx" 的结果字符串
     */
    public static String test() {
        StringBuilder result = new StringBuilder();
        // 避免声明被混淆移除
        @SuppressWarnings("unused")
        String helper = new Helper().greet("Reflection");

        try {
            // ---- 1. Class.forName() 加载自身 ----
            Class<?> clazz = Class.forName("com.flora.tangle.testbed.tricky.ReflectionTest");
            result.append("类加载:").append(clazz.getSimpleName());

            // ---- 2. 通过反射调用私有静态方法 ----
            Method staticMethod = clazz.getDeclaredMethod("privateStaticMethod", String.class);
            staticMethod.setAccessible(true);
            Object staticResult = staticMethod.invoke(null, "反射测试参数");
            result.append(" | 静态方法:").append(staticResult);

            // ---- 3. 通过反射调用私有实例方法 ----
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Method instanceMethod = clazz.getDeclaredMethod("privateInstanceMethod", int.class, int.class);
            instanceMethod.setAccessible(true);
            Object instResult = instanceMethod.invoke(instance, 7, 3);
            result.append(" | 实例方法:7*3+7-3=").append(instResult);

            // ---- 4. 通过反射访问私有静态字段 ----
            Field staticField = clazz.getDeclaredField("privateStaticField");
            staticField.setAccessible(true);
            String oldStaticVal = (String) staticField.get(null);
            staticField.set(null, "反射修改后的静态值");
            String newStaticVal = (String) staticField.get(null);
            result.append(" | 静态字段:").append(oldStaticVal).append("->").append(newStaticVal);

            // ---- 5. 通过反射访问私有实例字段 ----
            Field instanceField = clazz.getDeclaredField("privateInstanceField");
            instanceField.setAccessible(true);
            String oldInstVal = (String) instanceField.get(instance);
            instanceField.set(instance, "反射修改后的实例值");
            String newInstVal = (String) instanceField.get(instance);
            result.append(" | 实例字段:").append(oldInstVal).append("->").append(newInstVal);

            // ---- 6. 反射调用受保护方法 ----
            Method protMethod = clazz.getDeclaredMethod("protectedMethod", String.class);
            protMethod.setAccessible(true);
            Object protResult = protMethod.invoke(instance, "测试保护方法");
            result.append(" | 保护方法:").append(protResult);

            // ---- 7. 反射调用包级方法 ----
            Method pkgMethod = clazz.getDeclaredMethod("packageMethod", String[].class);
            pkgMethod.setAccessible(true);
            Object pkgResult = pkgMethod.invoke(instance, new Object[]{new String[]{"a", "b", "c"}});
            result.append(" | 包方法:").append(pkgResult);

            // ---- 8. 获取所有声明的方法（验证不因混淆丢失） ----
            Method[] declaredMethods = clazz.getDeclaredMethods();
            int methodCount = declaredMethods.length;
            result.append(" | 声明方法数=").append(methodCount);

            // ---- 9. 获取所有声明的字段 ----
            Field[] declaredFields = clazz.getDeclaredFields();
            int fieldCount = declaredFields.length;
            result.append(" | 声明字段数=").append(fieldCount);

        } catch (ClassNotFoundException e) {
            result.append(" | 错误:类未找到");
        } catch (NoSuchMethodException e) {
            result.append(" | 错误:方法未找到 - ").append(e.getMessage());
        } catch (NoSuchFieldException e) {
            result.append(" | 错误:字段未找到 - ").append(e.getMessage());
        } catch (InvocationTargetException e) {
            result.append(" | 错误:调用目标异常 - ").append(e.getCause().getMessage());
        } catch (IllegalAccessException e) {
            result.append(" | 错误:非法访问 - ").append(e.getMessage());
        } catch (InstantiationException e) {
            result.append(" | 错误:实例化失败");
        }

        return "ReflectionTest:OK:" + result.toString();
    }
}
