package com.flora.root.java.converter;

import com.flora.root.java.ConversionContext;
import com.flora.root.java.Converter;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bean 与 Map 互相转换的共享反射支撑。
 * <p>提供「是否为可映射 Bean 类型」的判定、属性内省（getter/setter/record 组件/字段回退）、
 * 以及带递归与循环引用检测的值转换。被 {@link BeanConverter} 与 {@link MapConverter} 复用。</p>
 */
final class BeanSupport {

    private BeanSupport() {
    }

    /**
     * 判断类型是否为「可映射 Bean」：非基础类型、非数组、非接口、非枚举、非 JDK/javax/sun 内置类型、
     * 非 Map/Collection 子类型，且可构造（有 public 无参构造器或为 record）。
     *
     * @param type 待判断的类型
     * @return 若为可映射 Bean 则返回 true
     */
    static boolean isBeanType(Class<?> type) {
        if (type == null || type.isPrimitive() || type.isArray() || type.isInterface()
                || type.isEnum() || Modifier.isAbstract(type.getModifiers())) {
            return false;
        }
        String pkg = type.getPackage() == null ? "" : type.getPackage().getName();
        if (pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg.startsWith("sun.")) {
            return false;
        }
        if (Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
            return false;
        }
        if (type.isRecord()) {
            return true;
        }
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            return Modifier.isPublic(ctor.getModifiers());
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * 属性描述：名称、类型、集合/数组元素类型、读取方法（getter 或 record 访问器）、
     * 写入方法（setter）及回退字段。
     */
    record Property(String name, Class<?> type, Class<?> elementType,
                    Method read, Method write, Field field) {
    }

    /**
     * 内省一个 Bean 类型的可读写属性。
     *
     * @param beanType Bean 类型
     * @return 属性名到属性的有序映射
     */
    static Map<String, Property> describe(Class<?> beanType) {
        Map<String, Property> props = new LinkedHashMap<>();
        if (beanType.isRecord()) {
            for (RecordComponent rc : beanType.getRecordComponents()) {
                props.put(rc.getName(), new Property(rc.getName(), rc.getType(),
                        elementTypeOf(rc.getGenericType()), rc.getAccessor(), null, null));
            }
            return props;
        }
        for (Method m : beanType.getMethods()) {
            if (m.getDeclaringClass() == Object.class) {
                continue;
            }
            if (m.getParameterCount() != 0 || m.getReturnType() == void.class) {
                continue;
            }
            String name = m.getName();
            if (name.startsWith("get") && name.length() > 3) {
                putGetter(props, decapitalize(name.substring(3)), m);
            } else if (name.startsWith("is") && name.length() > 2
                    && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                putGetter(props, decapitalize(name.substring(2)), m);
            }
        }
        for (Method m : beanType.getMethods()) {
            if (m.getParameterCount() == 1 && m.getName().startsWith("set") && m.getName().length() > 3) {
                props.computeIfAbsent(decapitalize(m.getName().substring(3)),
                        k -> new Property(k, m.getParameterTypes()[0],
                                elementTypeOf(m.getGenericParameterTypes()[0]), null, m, null));
                Property p = props.get(decapitalize(m.getName().substring(3)));
                if (p.read() != null) {
                    props.put(p.name(), new Property(p.name(), p.type(), p.elementType(), p.read(), m, null));
                }
            }
        }
        for (Field f : beanType.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            props.computeIfAbsent(f.getName(),
                    k -> new Property(k, f.getType(), elementTypeOf(f.getGenericType()), null, null, f));
        }
        return props;
    }

    private static void putGetter(Map<String, Property> props, String name, Method getter) {
        props.computeIfAbsent(name, k -> new Property(k, getter.getReturnType(),
                elementTypeOf(getter.getGenericReturnType()), getter, null, null));
    }

    private static String decapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * 从泛型类型中提取集合/数组的元素类型（ParameterizedType 的首类型参数）。
     *
     * @param genericType 泛型类型
     * @return 元素类型，无法提取时返回 null
     */
    static Class<?> elementTypeOf(Type genericType) {
        if (genericType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length >= 1 && args[0] instanceof Class<?> c) {
                return c;
            }
        }
        return null;
    }

    /**
     * 读取 Bean 实例的属性值（优先 getter，回退到字段）。
     *
     * @param property 属性
     * @param bean     Bean 实例
     * @return 属性值，读取失败返回 null
     */
    static Object read(Property property, Object bean) {
        try {
            if (property.read() != null) {
                return property.read().invoke(bean);
            }
            if (property.field() != null) {
                property.field().setAccessible(true);
                return property.field().get(bean);
            }
        } catch (ReflectiveOperationException e) {
            return null;
        }
        return null;
    }

    /**
     * 写入 Bean 实例的属性值（优先 setter，回退到字段）。
     *
     * @param property 属性
     * @param bean     Bean 实例
     * @param value    待写入的值
     */
    static void write(Property property, Object bean, Object value) {
        try {
            if (property.write() != null) {
                property.write().invoke(bean, value);
                return;
            }
            if (property.field() != null) {
                property.field().setAccessible(true);
                property.field().set(bean, value);
            }
        } catch (ReflectiveOperationException ignored) {
            // 写入失败（如类型不兼容）静默跳过，保持与“尽力而为”的映射语义一致
        }
    }

    /**
     * 将值转换为目标类型（含集合/数组元素递归）。
     *
     * @param value        待转换的值
     * @param targetType   目标类型（标量转换时使用）
     * @param elementTarget 集合/数组元素的目标类型；非 null 且 value 为集合/数组时对每个元素递归
     * @param registry     当前注册中心
     * @return 转换后的值
     */
    static Object convertValue(Object value, Class<?> targetType, Class<?> elementTarget,
                               ConverterRegistry registry) {
        if (value == null) {
            return null;
        }
        if (elementTarget != null && (value instanceof Collection<?> || value.getClass().isArray())) {
            return convertEach(value, elementTarget, registry);
        }
        Converter converter = registry.find(value.getClass(), targetType, elementTarget);
        return converter == null ? value : converter.convert(value, targetType, elementTarget);
    }

    private static Object convertEach(Object value, Class<?> elementTarget, ConverterRegistry registry) {
        if (value instanceof Collection<?> col) {
            List<Object> out = new ArrayList<>(col.size());
            for (Object elem : col) {
                out.add(convertValue(elem, elementTarget, null, registry));
            }
            return out;
        }
        int len = Array.getLength(value);
        Class<?> component = value.getClass().getComponentType();
        Object arr = Array.newInstance(component.isPrimitive() ? component : Object.class, len);
        for (int i = 0; i < len; i++) {
            Array.set(arr, i, convertValue(Array.get(value, i), elementTarget, null, registry));
        }
        return arr;
    }

    /**
     * 获取当前转换上下文的注册中心；缺失时回退到默认注册中心。
     */
    static ConverterRegistry currentRegistry() {
        ConverterRegistry registry = ConversionContext.currentRegistry();
        return registry != null ? registry : ConverterRegistry.newInstance();
    }

    /**
     * 循环引用检测的线程本地集合（基于对象同一性）。
     */
    private static final ThreadLocal<Set<Object>> VISITED =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new java.util.IdentityHashMap<>()));

    /**
     * 在转换入口包装循环引用检测：同一对象重复出现则抛 {@link IllegalStateException}。
     *
     * @param source 来源对象
     * @param action 实际转换动作
     * @param <T>    结果类型
     * @return 转换结果
     */
    static <T> T guardCycle(Object source, java.util.function.Supplier<T> action) {
        Set<Object> visited = VISITED.get();
        if (!visited.add(source)) {
            throw new IllegalStateException("检测到循环引用");
        }
        try {
            return action.get();
        } finally {
            visited.remove(source);
        }
    }
}
