package com.flora.java.converter;

import com.flora.cache.store.MemoryCache;
import com.flora.java.CheckUtil;
import com.flora.java.Converter;
import com.flora.java.TargetMatcher;
import com.flora.java.clazz.TypeDistanceCalculator;
import com.flora.tag.LogicFragile;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 转换器注册中心，管理和查找类型转换器。
 * <p>
 * 内置了常用类型的转换器（布尔、数字、字符串、日期、枚举、数组、集合、Optional），
 * 同时支持通过 SPI 机制加载自定义转换器。
 * 查找转换器时会综合考虑来源类型匹配度、目标类型匹配度以及优先级。
 * </p>
 */
public final class ConverterRegistry {

    private static final int MAX_DISTANCE = Integer.MAX_VALUE >>1;
    // 排名字段最大值，必须 ≤ Short.MAX_VALUE 以安全地 packed 进 (targetRank << 16) | elementRank
    private static final int MAX_RANK = Short.MAX_VALUE >>1;
    private static final List<Converter> BUILT_IN_CONVERTERS = new CopyOnWriteArrayList<>();
    private static final List<Converter> SPI_CONVERTERS = new CopyOnWriteArrayList<>();
    private static final List<Converter> ALL_CONVERTERS = new CopyOnWriteArrayList<>();

    private static final Converter NOOP_CONVERTER = new NoopConverter();

    static {
        BUILT_IN_CONVERTERS.add(new BooleanConverter());
        BUILT_IN_CONVERTERS.add(new NumberConverter());
        BUILT_IN_CONVERTERS.add(new StringConverter());
        BUILT_IN_CONVERTERS.add(new DateConverter());
        BUILT_IN_CONVERTERS.add(new EnumConverter());
        BUILT_IN_CONVERTERS.add(new ArrayConverter());
        BUILT_IN_CONVERTERS.add(new CollectionConverter());
        BUILT_IN_CONVERTERS.add(new OptionalConverter());

        SPI_CONVERTERS.addAll(ServiceLoader.load(Converter.class).stream().map(ServiceLoader.Provider::get).toList());

        ALL_CONVERTERS.addAll(BUILT_IN_CONVERTERS);
        ALL_CONVERTERS.addAll(SPI_CONVERTERS);
    }
    private final List<Converter> executors;
    private final MemoryCache<FindKey, Converter> cache = new MemoryCache<>();
    private ConverterRegistry(List<Converter> executors){
        this.executors = new CopyOnWriteArrayList<>(executors);
    }

    private record FindKey(Class<?> source, Class<?> target, Class<?> elementType) {}

    /**
     * 创建一个新的转换器注册中心实例，加载内置转换器和 SPI 转换器。
     *
     * @return 转换器注册中心实例
     */
    public static ConverterRegistry newInstance() {
        return newInstance(true,true);
    }

    /**
     * 创建一个新的转换器注册中心实例，可控制是否加载内置和 SPI 转换器。
     *
     * @param loadInnerConverter 是否加载内置转换器
     * @param loadSpiConverter   是否加载 SPI 转换器
     * @return 转换器注册中心实例
     */
    public static ConverterRegistry newInstance(boolean loadInnerConverter,boolean loadSpiConverter) {
        if(loadInnerConverter){
            if(loadSpiConverter){
                return new ConverterRegistry(ALL_CONVERTERS);
            }else{
                return new ConverterRegistry(BUILT_IN_CONVERTERS);
            }
        }else{
            if(loadSpiConverter){
                return new ConverterRegistry(SPI_CONVERTERS);
            }else{
                return new ConverterRegistry(List.of());
            }
        }
    }

    /**
     * 注册一个新的转换器。
     *
     * @param executor 转换器实例，不能为 null
     */
    public void register(Converter executor) {
        CheckUtil.notNull(executor, "转换器不能为空");
        executors.add(executor);
    }

    /**
     * 查找能将来源类型转换为目标类型的转换器，考虑元素类型。
     *
     * @param sourceType  来源类型
     * @param targetType  目标类型，不能为 null
     * @param elementType 元素类型（用于集合/数组转换），可为 null
     * @return 匹配的转换器，若无匹配则返回 null
     */
    public Converter find(Class<?> sourceType, Class<?> targetType, Class<?> elementType) {
        CheckUtil.notNull(targetType, "目标类型不能为空");
        assert sourceType != null : "sourceType 应来自 value.getClass()，调用方需保证非空";
        FindKey key = new FindKey(sourceType, targetType, elementType);
        Converter cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Converter result = resolve(sourceType, targetType, elementType);
        if (result != null) {
            cache.put(key, result);
        }
        return result;
    }

    /**
     * 解析匹配的转换器。通过多层过滤（来源匹配、目标匹配、最高优先级、最短来源距离、最短目标距离）
     * 从候选转换器中选取最优的一个。若最终剩余多个转换器则抛出异常。
     *
     * @param sourceType  来源类型
     * @param targetType  目标类型
     * @param elementType 元素类型
     * @return 最优的转换器，若无匹配则返回 null
     */
    @LogicFragile("转换器的过滤顺序经过严格评估，先匹配source和target，然后优先级，然后再排序source和target，这是唯一正确的顺序")
    private Converter resolve(Class<?> sourceType, Class<?> targetType, Class<?> elementType) {
        if (elementType == null && targetType.isAssignableFrom(sourceType)) {
            return NOOP_CONVERTER;
        }

        List<Converter> candidates = new ArrayList<>(executors);
        candidates = filterBySourceMatch(candidates, sourceType);
        candidates = filterByTargetMatch(candidates, targetType, elementType);
        candidates = filterByMaxPriority(candidates);
        candidates = filterByMinSourceDistance(candidates, sourceType);
        candidates = filterByMinTargetDistance(candidates, targetType, elementType);

        if (candidates.isEmpty()) {
            return null;
        }else if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        Converter a = candidates.get(0);
        Converter b = candidates.get(1);
        throw new IllegalStateException("发现重复的转换器："
                + a.getClass().getName() + " 与 " + b.getClass().getName()
                + " 均能处理 " + sourceType.getName() + " -> " + targetType.getName()
                + "，且来源类型具体度、目标类型具体度与优先级均相同");
    }

    /**
     * 过滤出声明类型可匹配来源类型的转换器。
     */
    private static List<Converter> filterBySourceMatch(List<Converter> list, Class<?> sourceType) {
        return list.stream()
                .filter(e -> e.declareSourceTypes().stream().anyMatch(st -> st.isAssignableFrom(sourceType)))
                .toList();
    }

    /**
     * 过滤出目标匹配器可匹配目标类型的转换器。
     */
    private static List<Converter> filterByTargetMatch(List<Converter> list, Class<?> targetType, Class<?> elementType) {
        return list.stream()
                .filter(e -> e.declareTargetMatcher().matches(targetType, elementType))
                .toList();
    }

    /**
     * 过滤出来源类型距离最短的转换器。
     */
    private static List<Converter> filterByMinSourceDistance(List<Converter> list, Class<?> sourceType) {
        int min = list.stream()
                .mapToInt(e -> sourceDistance(e, sourceType))
                .filter(d -> d >= 0)
                .min()
                .orElse(MAX_DISTANCE);
        return list.stream()
                .filter(e -> sourceDistance(e, sourceType) == min)
                .toList();
    }

    /**
     * 过滤出目标类型距离最短的转换器。
     */
    private static List<Converter> filterByMinTargetDistance(List<Converter> list, Class<?> targetType, Class<?> elementType) {
        int min = list.stream()
                .mapToInt(e -> targetRejectionDistance(e.declareTargetMatcher(), targetType, elementType))
                .min()
                .orElse(MAX_DISTANCE);
        return list.stream()
                .filter(e -> targetRejectionDistance(e.declareTargetMatcher(), targetType, elementType) == min)
                .toList();
    }

    /**
     * 过滤出优先级最高的转换器。
     */
    private static List<Converter> filterByMaxPriority(List<Converter> list) {
        int max = list.stream()
                .mapToInt(Converter::declarePriority)
                .max()
                .orElse(0);
        return list.stream()
                .filter(e -> e.declarePriority() == max)
                .toList();
    }

    /**
     * 计算转换器声明类型与来源类型之间的继承距离。
     *
     * @return 距离值，若无匹配则返回 -1
     */
    private static int sourceDistance(Converter executor, Class<?> sourceType) {
        int min = MAX_DISTANCE;
        for (Class<?> declared : executor.declareSourceTypes()) {
            if (declared.isAssignableFrom(sourceType)) {
                int distance = TypeDistanceCalculator.inheritDistance(sourceType, declared);
                if (distance < min) {
                    min = distance;
                }
            }
        }
        return min == MAX_DISTANCE ? -1 : min;
    }

    /**
     * 计算目标类型和元素类型与目标匹配器的匹配距离。
     * <p>
     * 从目标类型本身开始沿继承链向上，计算连续匹配的层数作为 targetRank；
     * 同理对 elementType 计算 elementRank。返回值将两个 rank 打包为一个 int。
     * rank 值越大表示匹配越具体（距离越近）。
     * </p>
     *
     * @param matcher     目标类型匹配器
     * @param targetType  目标类型
     * @param elementType 元素类型
     * @return 打包后的距离值（高16位为 targetRank，低16位为 elementRank）
     */
    public static int targetRejectionDistance(TargetMatcher matcher, Class<?> targetType, Class<?> elementType) {

        int passCount = 0;
        for(Class<?> current = targetType;current != null && matcher.matches(current, elementType);current = current.getSuperclass()) {
            passCount++;
        }
        int targetRank = passCount == 0 ? MAX_RANK : passCount;

        passCount = 0;
        for(Class<?> current = elementType;current != null && matcher.matches(targetType, current);current = current.getSuperclass()) {
            passCount++;
        }
        int elementRank = passCount == 0 ? MAX_RANK : passCount;
        return (targetRank << 16) | elementRank;
    }


}
