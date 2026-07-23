package com.flora.data;

import com.flora.java.CheckUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * 类操作工具类，提供类加载、反射创建实例、接口/父类获取等功能。
 */
public final class ClassUtil {

    private ClassUtil() {
    }

    /**
     * 获取当前线程的上下文类加载器，如果为空则返回系统类加载器。
     *
     * @return 类加载器
     */
    public static ClassLoader getContextClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
            return cl;
        }
        return ClassLoader.getSystemClassLoader();
    }

    /**
     * 根据类名加载类（使用当前线程的上下文类加载器）。
     *
     * @param className 完整类名，不能为空
     * @return 加载的类对象
     * @throws RuntimeException 如果未找到类
     */
    public static Class<?> loadClass(String className) {
        CheckUtil.notEmpty(className, "类名不能为空");
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("未找到类: " + className, e);
        }
    }

    /**
     * 根据类名和指定的类加载器加载类。
     *
     * @param className   完整类名，不能为空
     * @param classLoader 类加载器，不能为空
     * @return 加载的类对象
     * @throws RuntimeException 如果未找到类
     */
    public static Class<?> loadClass(String className, ClassLoader classLoader) {
        CheckUtil.notEmpty(className, "类名不能为空");
        CheckUtil.notNull(classLoader, "类加载器不能为空");
        try {
            return Class.forName(className, true, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("未找到类: " + className, e);
        }
    }

    /**
     * 判断类是否为抽象类。
     *
     * @param clazz 待判断的类
     * @return 如果是抽象类返回 true
     */
    public static boolean isAbstract(Class<?> clazz) {
        return Modifier.isAbstract(clazz.getModifiers());
    }

    /**
     * 判断类是否为接口。
     *
     * @param clazz 待判断的类
     * @return 如果是接口返回 true
     */
    public static boolean isInterface(Class<?> clazz) {
        return clazz.isInterface();
    }

    /**
     * 判断类是否为原始类型。
     *
     * @param clazz 待判断的类
     * @return 如果是原始类型返回 true
     */
    public static boolean isPrimitive(Class<?> clazz) {
        return clazz.isPrimitive();
    }

    /**
     * 获取类的简名（不含包名）。
     *
     * @param clazz 类，不能为空
     * @return 类简名
     */
    public static String getShortClassName(Class<?> clazz) {
        CheckUtil.notNull(clazz, "类不能为空");
        return clazz.getSimpleName();
    }

    /**
     * 获取类的全限定名。
     *
     * @param clazz 类，不能为空
     * @return 全限定类名
     */
    public static String getQualifiedClassName(Class<?> clazz) {
        CheckUtil.notNull(clazz, "类不能为空");
        return clazz.getName();
    }

    /**
     * 获取类及其父类实现的所有接口。
     *
     * @param clazz 待检查的类
     * @return 不可修改的接口集合
     */
    public static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        Set<Class<?>> interfaces = new HashSet<>();
        Class<?> current = clazz;
        while (current != null) {
            Class<?>[] ifaces = current.getInterfaces();
            Collections.addAll(interfaces, ifaces);
            current = current.getSuperclass();
        }
        return Collections.unmodifiableSet(interfaces);
    }

    /**
     * 获取类及其父类的完整继承链。
     *
     * @param clazz 待检查的类
     * @return 不可修改的父类列表（从当前类到 Object）
     */
    public static List<Class<?>> getAllSuperclasses(Class<?> clazz) {
        List<Class<?>> classes = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null) {
            classes.add(current);
            current = current.getSuperclass();
        }
        return Collections.unmodifiableList(classes);
    }

    /**
     * 通过无参构造器创建类的新实例（会设置构造器可访问）。
     *
     * @param clazz 待实例化的类，不能为空
     * @param <T>   类型
     * @return 新实例
     * @throws RuntimeException 如果没有无参构造器或实例化失败
     */
    public static <T> T newInstance(Class<T> clazz) {
        CheckUtil.notNull(clazz, "类不能为空");
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("类 " + clazz.getName() + " 没有无参构造器", e);
        } catch (Exception e) {
            throw new RuntimeException("实例化 " + clazz.getName() + " 失败", e);
        }
    }

    /**
     * 判断指定类名的类是否存在于类路径中。
     *
     * @param className 完整类名
     * @return 如果类存在返回 true，否则返回 false
     */
    public static boolean hasClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
