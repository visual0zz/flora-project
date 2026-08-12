package com.flora.root.java.converter;

import com.flora.root.java.ConvertUtil;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BeanConverter（Map -> Bean）单元测试，全程经 {@link ConvertUtil} 门面验证无重复转换器冲突。
 */
class BeanConverterTest {

    @Test
    void flatMapToBean() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Alice");
        map.put("age", 30);
        BeanSamples.Person p = ConvertUtil.convert(BeanSamples.Person.class, map);
        assertEquals("Alice", p.getName());
        assertEquals(30, p.getAge());
    }

    @Test
    void roundTripBeanToMapToBean() {
        BeanSamples.Person src = new BeanSamples.Person();
        src.setName("Bob");
        src.setAge(25);
        Map<?, ?> map = ConvertUtil.convert(Map.class, src);
        assertTrue(map.containsKey("name"));
        assertEquals("Bob", map.get("name"));

        BeanSamples.Person back = ConvertUtil.convert(BeanSamples.Person.class, map);
        assertEquals("Bob", back.getName());
        assertEquals(25, back.getAge());
    }

    @Test
    void nestedBeanRecursion() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Carol");
        map.put("age", 40);
        Map<String, Object> addr = new LinkedHashMap<>();
        addr.put("city", "Shanghai");
        addr.put("zip", "200000");
        map.put("address", addr);

        BeanSamples.Person p = ConvertUtil.convert(BeanSamples.Person.class, map);
        assertNotNull(p.getAddress());
        assertEquals("Shanghai", p.getAddress().getCity());
        assertEquals("200000", p.getAddress().getZip());
    }

    @Test
    void collectionOfBeansRecursion() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Dave");
        map.put("age", 50);
        Map<String, Object> a1 = new LinkedHashMap<>();
        a1.put("city", "A");
        a1.put("zip", "1");
        Map<String, Object> a2 = new LinkedHashMap<>();
        a2.put("city", "B");
        a2.put("zip", "2");
        map.put("addresses", List.of(a1, a2));

        BeanSamples.Person p = ConvertUtil.convert(BeanSamples.Person.class, map);
        assertNotNull(p.getAddresses());
        assertEquals(2, p.getAddresses().size());
        assertEquals("A", p.getAddresses().get(0).getCity());
        assertEquals("B", p.getAddresses().get(1).getCity());
    }

    @Test
    void recordFromMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", 3);
        map.put("y", 4);
        BeanSamples.Point point = ConvertUtil.convert(BeanSamples.Point.class, map);
        assertEquals(3, point.x());
        assertEquals(4, point.y());
    }

    @Test
    void nonBeanTargetNotMatched() {
        // String -> Map：源不是 Bean，MapConverter 源匹配器拒绝，应“未找到”而非虚假承诺
        assertThrows(IllegalArgumentException.class,
                () -> ConvertUtil.convert(Map.class, "just a string"));
    }

    @Test
    void nonConstructableTargetThrows() {
        // NoDefaultCtor 无无参构造器且非 record -> 非 Bean -> 无转换器 -> 报错
        assertThrows(IllegalArgumentException.class,
                () -> ConvertUtil.convert(BeanSamples.NoDefaultCtor.class, Map.of("value", "x")));
    }

    @Test
    void circularReferenceThrows() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Loop");
        map.put("age", 1);
        map.put("self", map); // 自引用
        assertThrows(IllegalStateException.class,
                () -> ConvertUtil.convert(BeanSamples.Person.class, map));
    }
}
