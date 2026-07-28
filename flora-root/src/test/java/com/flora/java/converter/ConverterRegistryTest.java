package com.flora.java.converter;

import com.flora.java.Converter;
import com.flora.java.TypeMatcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConverterRegistry 转换器注册表的单元测试。
 * 测试 NoopConverter、多阶段过滤（优先级/源类型特化）、通配目标匹配、注册机制及缓存行为。
 */
class ConverterRegistryTest {

    private static final class FixedConverter implements Converter {
        private final Class<?> source;
        private final Class<?> target;
        private final int priority;

        FixedConverter(Class<?> source, Class<?> target, int priority) {
            this.source = source;
            this.target = target;
            this.priority = priority;
        }

        @Override
        public Collection<Class<?>> declareSourceTypes() {
            return List.of(source);
        }

        @Override
        public Collection<Class<?>> declareTargetTypes() {
            return List.of(target);
        }

        @Override
        public int declarePriority() {
            return priority;
        }

        @Override
        public Object convert(Object obj, Class<?> targetType, Class<?> elementType) {
            return obj;
        }
    }

    private enum Color {RED, GREEN}

    // ==================== NoopConverter（identity / upcast） ====================

    /**
     * 测试相同类型匹配到 NoopConverter 返回原值。
     */
    @Test
    void noopOnIdentity() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        Converter c = registry.find(String.class, String.class, null);
        assertNotNull(c);
        assertSame("x", c.convert("x", String.class));
    }

    /**
     * 测试向上转型匹配到 NoopConverter。
     */
    @Test
    void noopOnUpcast() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        Converter c = registry.find(Integer.class, Number.class, null);
        assertNotNull(c);
        assertSame(Integer.valueOf(7), c.convert(7, Number.class));
    }

    // ==================== 无匹配 ====================

    /**
     * 测试无匹配转换器时返回 null。
     */
    @Test
    void noMatchReturnsNull() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        assertNull(registry.find(Integer.class, String.class, null));
    }

    // ==================== 多阶段过滤 ====================

    /**
     * 测试源类型更具体的转换器优先匹配。
     */
    @Test
    void moreSpecificSourceWins() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        FixedConverter numberWide = new FixedConverter(Number.class, String.class, 0);
        FixedConverter objectWide = new FixedConverter(Object.class, String.class, 0);
        registry.register(numberWide);
        registry.register(objectWide);
        assertSame(numberWide, registry.find(Integer.class, String.class, null));
    }

    /**
     * 测试优先级更高的转换器获胜。
     */
    @Test
    void priorityTiebreaker() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        FixedConverter high = new FixedConverter(Object.class, String.class, 1);
        FixedConverter low = new FixedConverter(Object.class, String.class, 0);
        registry.register(high);
        registry.register(low);
        assertSame(high, registry.find(Integer.class, String.class, null));
    }

    /**
     * 测试存在重复转换器时抛出异常。
     */
    @Test
    void duplicateConverterThrows() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        registry.register(new FixedConverter(Object.class, String.class, 0));
        registry.register(new FixedConverter(Object.class, String.class, 0));
        assertThrows(IllegalStateException.class, () -> registry.find(Integer.class, String.class, null));
    }

    /**
     * 测试优先级高于源类型特异性。
     */
    @Test
    void priorityDominatesSourceSpecificity() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        FixedConverter highPriority = new FixedConverter(Object.class, String.class, 5);
        FixedConverter lowPriority = new FixedConverter(Number.class, String.class, 0);
        registry.register(highPriority);
        registry.register(lowPriority);
        assertSame(highPriority, registry.find(Integer.class, String.class, null));
    }

    // ==================== 通配目标类型匹配 ====================

    /**
     * 测试数组目标类型的通配匹配。
     */
    @Test
    void wildcardArrayMatches() {
        ConverterRegistry registry = ConverterRegistry.newInstance();
        assertInstanceOf(ArrayConverter.class, registry.find(ArrayList.class, String[].class, null));
    }

    /**
     * 测试集合目标类型的通配匹配。
     */
    @Test
    void wildcardCollectionMatches() {
        ConverterRegistry registry = ConverterRegistry.newInstance();
        assertInstanceOf(CollectionConverter.class, registry.find(String.class, List.class, null));
    }

    /**
     * 测试枚举目标类型的通配匹配。
     */
    @Test
    void wildcardEnumMatches() {
        ConverterRegistry registry = ConverterRegistry.newInstance();
        assertInstanceOf(EnumConverter.class, registry.find(String.class, Color.class, null));
        assertInstanceOf(EnumConverter.class, registry.find(Integer.class, Color.class, null));
    }

    /**
     * 测试 Optional 目标类型的解包。
     */
    @Test
    void optionalTargetResolves() {
        ConverterRegistry registry = ConverterRegistry.newInstance();
        Converter c = registry.find(String.class, Optional.class, null);
        assertNotNull(c);
        assertEquals(Optional.of("x"), c.convert("x", Optional.class));
    }

    /**
     * 测试数组源类型到集合目标类型匹配 CollectionConverter。
     */
    @Test
    void arraySourceToCollectionResolvesCollectionConverter() {
        ConverterRegistry registry = ConverterRegistry.newInstance();
        Converter c = registry.find(String[].class, List.class, null);
        assertInstanceOf(CollectionConverter.class, c);
    }

    // ==================== 注册机制 ====================

    /**
     * 测试注册新转换器后能找到匹配。
     */
    @Test
    void registerConverterIncreasesMatch() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        assertNull(registry.find(Integer.class, String.class, null));
        registry.register(new FixedConverter(Integer.class, String.class, 0));
        assertNotNull(registry.find(Integer.class, String.class, null));
    }

    /**
     * 测试注册 null 转换器时抛出异常。
     */
    @Test
    void registerNullConverterThrows() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    /**
     * 测试查找时传入 null 目标类型抛出异常。
     */
    @Test
    void findWithNullTargetThrows() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        assertThrows(NullPointerException.class, () -> registry.find(String.class, null, null));
    }

    // ==================== 工厂方法组合 ====================

    /**
     * 测试 newInstance 默认加载所有转换器。
     */
    @Test
    void newInstanceDefaultLoadsAll() {
        ConverterRegistry registry = ConverterRegistry.newInstance();
        assertNotNull(registry.find(String.class, Integer.class, null));   // NumberConverter
        assertNotNull(registry.find(String.class, String.class, null));    // StringConverter
    }

    /**
     * 测试 newInstance(false, false) 不加载任何转换器。
     */
    @Test
    void newInstanceEmptyLoadsNone() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        assertNull(registry.find(String.class, Integer.class, null));     // 无内置转换器
    }

    /**
     * 测试 newInstance 仅加载 SPI 转换器。
     */
    @Test
    void newInstanceOnlySpi() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, true);
        assertNull(registry.find(String.class, Integer.class, null));     // SPI 中通常无内置转换器
    }

    /**
     * 测试 newInstance 仅加载内置转换器。
     */
    @Test
    void newInstanceOnlyInner() {
        ConverterRegistry registry = ConverterRegistry.newInstance(true, false);
        assertNotNull(registry.find(String.class, Integer.class, null));   // 内置 NumberConverter
    }

    // ==================== 缓存行为 ====================

    /**
     * 测试 find 结果缓存：无 register 干扰时，两次相同查询命中同一缓存实例。
     */
    @Test
    void findUsesCache() {
        ConverterRegistry registry = ConverterRegistry.newInstance();
        Converter c1 = registry.find(String.class, Integer.class, null);
        assertNotNull(c1);
        // 第二次查询应命中缓存，返回同一实例
        Converter c2 = registry.find(String.class, Integer.class, null);
        assertSame(c1, c2);
    }

    // ==================== targetRejectionDistance（接口类型正确分层） ====================

    /**
     * 接口目标类型的 targetRejectionDistance 应正确反映继承深度，而非全部为 1。
     * <p>对于 List 类型，Object 匹配器应经过 List→Collection→Iterable 三级匹配。</p>
     */
    @Test
    void targetRejectionDistanceInterfaceRankReflectsDepth() {
        TypeMatcher matchesObject = (type, elem) -> Object.class.isAssignableFrom(type);

        // ArrayList（类）→ 应有完整继承深度（ArrayList→AbstractList→AbstractCollection→Object→...）
        int arrayListDist = ConverterRegistry.targetRejectionDistance(matchesObject, ArrayList.class, null);
        int arrayListTargetRank = arrayListDist >>> 16;
        assertTrue(arrayListTargetRank >= 4,
                "ArrayList should have targetRank >= 4, got " + arrayListTargetRank);

        // List（接口）→ 应有接口继承深度（List→Collection→Iterable→beyond）
        int listDist = ConverterRegistry.targetRejectionDistance(matchesObject, List.class, null);
        int listTargetRank = listDist >>> 16;
        assertTrue(listTargetRank >= 3,
                "List should have targetRank >= 3, got " + listTargetRank);

        // 关键：接口的 rank 不应为 1（旧 bug 的表现）
        assertTrue(listTargetRank > 1,
                "Interface targetRank should be > 1, was " + listTargetRank
                        + " (old bug: always 1 due to getSuperclass()=null)");
    }

    /**
     * 对于具体类匹配器（如只匹配 ArrayList），接口类型的 rank 应小于类的 rank。
     */
    @Test
    void targetRejectionDistanceSpecificMatcher() {
        // 只匹配 ArrayList 的匹配器
        TypeMatcher onlyArrayList = (type, elem) -> type == ArrayList.class;

        int dist = ConverterRegistry.targetRejectionDistance(onlyArrayList, ArrayList.class, null);
        int rank = dist >>> 16;
        assertEquals(1, rank, "Only root matches -> rank should be 1");

        // List 不匹配此匹配器 → 应得到一个很大的 rank 值（MAX_RANK 语义）
        int listDist = ConverterRegistry.targetRejectionDistance(onlyArrayList, List.class, null);
        int listRank = listDist >>> 16;
        assertTrue(listRank > 1000,
                "Non-matching type should get a large rank, got " + listRank);
    }

    // ==================== 元素转换复用当前注册中心（fix #1） ====================

    /**
     * String -> Integer 的哨兵转换器：优先级高于内置 NumberConverter，
     * 用于在集合元素转换场景中验证「使用的是当前注册中心而非全局 ConvertUtil」。
     */
    private static final class SentinelConverter implements Converter {
        @Override
        public Collection<Class<?>> declareSourceTypes() {
            return List.of(String.class);
        }

        @Override
        public Collection<Class<?>> declareTargetTypes() {
            return List.of(Integer.class);
        }

        @Override
        public int declarePriority() {
            return 10;
        }

        @Override
        public Object convert(Object obj, Class<?> targetType, Class<?> elementType) {
            return 999;
        }
    }

    /**
     * 验证集合元素转换复用「当前注册中心」的转换器集合（fix #1）。
     * 注册高优先级哨兵转换器后，通过 facade 转换 List&lt;String&gt; -&gt; List&lt;Integer&gt;，
     * 元素应命中哨兵（返回 999），而非全局 ConvertUtil 的内置 NumberConverter（返回 1）。
     */
    @Test
    void collectionElementUsesCurrentRegistry() {
        ConverterRegistry registry = ConverterRegistry.newInstance(true, false);
        registry.register(new SentinelConverter());
        ConvertFacade facade = new ConvertFacade(registry);
        List<?> result = facade.convertElements(List.of("1", "2"), List.class, Integer.class);
        assertEquals(List.of(999, 999), result);
    }

    // ==================== 谓词式来源匹配（declareSourceMatcher） ====================

    /**
     * 覆盖 {@link Converter#declareSourceMatcher()} 的转换器，仅匹配简单名为
     * "PredicateBean" 的来源类型，与 {@link #declareSourceTypes()} 返回的 {@link Object} 不同，
     * 用于验证 filterBySourceMatch 改用谓词而非固定 Class 集合。
     */
    private static final class PredicateSourceConverter implements Converter {
        @Override
        public Collection<Class<?>> declareSourceTypes() {
            return List.of(Object.class);
        }

        @Override
        public Collection<Class<?>> declareTargetTypes() {
            return List.of(String.class);
        }

        @Override
        public TypeMatcher declareSourceMatcher() {
            return (sourceType, elementType) ->
                    sourceType != null && "PredicateBean".equals(sourceType.getSimpleName());
        }

        @Override
        public Object convert(Object obj, Class<?> targetType, Class<?> elementType) {
            return "converted";
        }
    }

    private static final class PredicateBean {
    }

    /**
     * 测试覆盖了 declareSourceMatcher 的转换器，来源匹配由谓词决定而非 declareSourceTypes。
     */
    @Test
    void predicateSourceMatcherDrivesSourceMatch() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        registry.register(new PredicateSourceConverter());
        // 谓词匹配：PredicateBean -> String 命中
        assertNotNull(registry.find(PredicateBean.class, String.class, null));
        // 谓词拒绝：String 不是 PredicateBean，即便 declareSourceTypes 含 Object
        // 注意用非恒等式（String -> Integer）避免 identity 短路到 NoopConverter
        assertNull(registry.find(String.class, Integer.class, null));
    }

    /**
     * 测试默认 declareSourceMatcher（从 declareSourceTypes 推导）与原有行为一致，向后兼容。
     */
    @Test
    void defaultSourceMatcherMirrorsDeclareSourceTypes() {
        ConverterRegistry registry = ConverterRegistry.newInstance(false, false);
        FixedConverter objectWide = new FixedConverter(Object.class, String.class, 0);
        registry.register(objectWide);
        assertSame(objectWide, registry.find(Integer.class, String.class, null));
        assertSame(objectWide, registry.find(Long.class, String.class, null));
    }
}
