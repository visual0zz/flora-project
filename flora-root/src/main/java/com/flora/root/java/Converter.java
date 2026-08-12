package com.flora.root.java;

import java.util.Collection;
import java.util.List;

/**
 * 类型转换器接口，定义了将对象从来源类型转换为目标类型的基本契约。
 * <p>
 * 实现类需声明支持的来源类型和目标类型，并提供具体的转换逻辑。
 * 优先级机制允许在多个匹配的转换器中选择最优的一个。
 * </p>
 */
public interface Converter {

    /**
     * 返回转换器的优先级，数值越高优先被选中。
     *
     * @return 优先级值，默认返回 0
     */
    default int declarePriority() {return 0;}
    /**
     * 声明此转换器支持的来源类型集合。
     * <p>
     * 默认返回空集合。若转换器通过覆盖 {@link #declareSourceMatcher()}
     * 以谓词方式声明来源（如「任意 Bean」），则无需重写本方法；
     * 依赖类型集合做精确匹配的转换器应重写为具体的类型集合。
     *
     * @return 支持的来源类型集合
     */
    default Collection<Class<?>> declareSourceTypes() {
        return List.of();
    }
    /**
     * 声明此转换器支持的目标类型集合。
     * <p>
     * 默认返回空集合。若转换器通过覆盖 {@link #declareTargetMatcher()}
     * 以谓词方式声明目标（如「任意 Bean」），则无需重写本方法；
     * 依赖类型集合做精确匹配的转换器应重写为具体的类型集合。
     *
     * @return 支持的目标类型集合
     */
    default Collection<Class<?>> declareTargetTypes() {
        return List.of();
    }
    /**
     * 将对象转换为指定目标类型。
     *
     * @param obj         待转换的对象
     * @param targetType  目标类型
     * @param elementType 元素类型（用于集合/数组类型的转换），可为 null
     * @return 转换后的对象
     */
    Object convert(Object obj, Class<?> targetType, Class<?> elementType);

    /**
     * 将对象转换为指定目标类型（无元素类型参数的重载方法）。
     *
     * @param obj        待转换的对象
     * @param targetType 目标类型
     * @return 转换后的对象
     */
    default Object convert(Object obj, Class<?> targetType) {
        return convert(obj, targetType, null);
    }

    /**
     * 声明目标类型匹配器，用于更精确地判断是否支持某目标类型。
     * <p>
     * 默认实现基于 {@link #declareTargetTypes()} 返回的类型集合进行精确匹配，
     * 对于 {@link Enum}、{@code Object[]} 和 {@link java.util.Collection}
     * 等泛化类型会进行子类型判断。
     * </p>
     *
     * @return 目标类型匹配器
     */
    default TypeMatcher declareTargetMatcher() {
        Collection<Class<?>> declaredTargets = declareTargetTypes();
        return (targetType, elementType) -> {
            for (Class<?> declared : declaredTargets) {
                if (declared == targetType) {
                    return true;
                }
                if (declared == Enum.class && targetType.isEnum()) {
                    return true;
                }
                if (declared == Object[].class && targetType.isArray()) {
                    return true;
                }
                if (declared == Collection.class && Collection.class.isAssignableFrom(targetType)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * 声明来源类型匹配器，用于更精确地判断是否支持某来源类型。
     * <p>
     * 默认实现基于 {@link #declareSourceTypes()} 返回的类型集合，沿用
     * {@code declared.isAssignableFrom(sourceType)} 的判定（与 {@code filterBySourceMatch}
     * 的原有行为一致），对已有转换器零影响、向后兼容。覆盖此方法可表达
     * 「来源是任意 Bean」等谓词式匹配，与目标侧 {@link #declareTargetMatcher()} 对称。
     * </p>
     *
     * @return 来源类型匹配器
     */
    default TypeMatcher declareSourceMatcher() {
        Collection<Class<?>> declaredSources = declareSourceTypes();
        return (sourceType, elementType) -> declaredSources.stream()
                .anyMatch(st -> st.isAssignableFrom(sourceType));
    }
}
