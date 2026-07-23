package com.flora.codec;

import com.flora.java.converter.ArrayConverter;
import com.flora.java.converter.CollectionConverter;
import com.flora.java.Converter;
import com.flora.java.converter.ConverterRegistry;
import com.flora.java.converter.EnumConverter;
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
     * 测试 find 结果缓存：注册新转换器后仍返回缓存结果。
     */
    @Test
    void findUsesCache() {
        ConverterRegistry registry = ConverterRegistry.newInstance();
        Converter c1 = registry.find(String.class, Integer.class, null);
        assertNotNull(c1);
        FixedConverter high = new FixedConverter(String.class, Integer.class, 999);
        registry.register(high);
        // 由于缓存存在，第二次查询应返回缓存结果
        Converter c2 = registry.find(String.class, Integer.class, null);
        assertSame(c1, c2);
    }
}
