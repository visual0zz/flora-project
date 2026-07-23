package com.flora.java.converter;

import com.flora.java.ConvertUtil;
import com.flora.java.Converter;
import com.flora.java.TargetMatcher;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 集合转换器，将任意对象转换为 {@link java.util.Collection} 类型。
 * <p>
 * 支持从集合、{@link Iterable}、数组或单个对象转换为指定类型的集合。
 * 可指定元素类型和元素转换器以对每个元素进行类型转换。
 * 支持包括 List、Set、Queue、Deque 及其常见实现的目标类型。
 * </p>
 */
public final class CollectionConverter implements Converter {

    /**
     * 创建默认集合转换器，不指定目标类型、元素转换器和元素类型。
     */
    public CollectionConverter() {
    }

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(Object.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(Collection.class);
    }

    @Override
    public TargetMatcher declareTargetMatcher() {
        return (targetType, elementType) ->
                Collection.class.isAssignableFrom(targetType)
                && (elementType == null || !Collection.class.isAssignableFrom(elementType));
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        Class<? extends Collection> result1;
        if (Collection.class.isAssignableFrom(toType)) {
            result1 = (Class<? extends Collection>) toType;
        } else {
            throw new IllegalArgumentException("CollectionConverter 仅支持集合目标类型，收到: " + toType.getName());
        }
        Class<? extends Collection> resolved = result1;
        if (resolved.isInstance(from) && elementType == null) {
            return from;
        }
        Collection<Object> result = createCollection(resolved);
        Collection<?> source = toCollection(from);
        if (elementType != null) {
            for (Object elem : source) {
                result.add(ConvertUtil.convert(elementType, elem));
            }
        } else {
            result.addAll(source);
        }
        return result;
    }

    /**
     * 根据指定的集合类型创建对应的集合实例。
     * 支持常见接口（List、Set、Queue、Deque 等）及其标准实现。
     *
     * @param type 集合类型
     * @return 创建的集合实例
     * @throws IllegalArgumentException 若无法实例化指定类型
     */
    private Collection<Object> createCollection(Class<? extends Collection> type) {
        if (type == Collection.class || type == List.class || type == ArrayList.class) {
            return new ArrayList<>();
        }
        if (type == Set.class || type == HashSet.class) {
            return new HashSet<>();
        }
        if (type == LinkedHashSet.class) {
            return new LinkedHashSet<>();
        }
        if (type == SortedSet.class || type == NavigableSet.class || type == TreeSet.class) {
            return new TreeSet<>();
        }
        if (type == Queue.class || type == Deque.class || type == ArrayDeque.class) {
            return new ArrayDeque<>();
        }
        if (type == LinkedList.class) {
            return new LinkedList<>();
        }
        if (!type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
            try {
                Constructor<?> ctor = type.getDeclaredConstructor();
                ctor.setAccessible(true);
                return (Collection<Object>) ctor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("无法实例化集合目标类型 " + type.getName()
                        + "：缺少可访问的无参构造器", e);
            }
        }
        throw new IllegalArgumentException("不支持的集合目标类型: " + type.getName()
                + "（仅支持 List/Set/LinkedHashSet/SortedSet/NavigableSet/Queue/Deque"
                + " 及其常见实现，或具有无参构造器的具体集合类）");
    }

    /**
     * 将任意值转换为 {@link Collection}。
     * 若已经是 {@link Collection} 则直接返回；
     * 若为 {@link Iterable} 则遍历收集；
     * 若为数组则逐元素收集；
     * 否则将单个值包装为单元素列表。
     */
    private static Collection<?> toCollection(Object value) {
        if (value instanceof Collection<?> c) {
            return c;
        }
        if (value instanceof Iterable<?> it) {
            List<Object> list = new ArrayList<>();
            for (Object elem : it) {
                list.add(elem);
            }
            return list;
        }
        if (value.getClass().isArray()) {
            List<Object> list = new ArrayList<>();
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                list.add(Array.get(value, i));
            }
            return list;
        }
        return List.of(value);
    }
}
