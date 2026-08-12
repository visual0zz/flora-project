package com.flora.root.java.converter;

import com.flora.root.java.ConvertUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MapConverter（Bean -> Map）单元测试，全程经 {@link ConvertUtil} 门面验证无重复转换器冲突。
 */
class MapConverterTest {

    @Test
    void beanToMapFlat() {
        BeanSamples.Person p = new BeanSamples.Person();
        p.setName("Alice");
        p.setAge(30);
        Map<?, ?> map = ConvertUtil.convert(Map.class, p);
        assertEquals("Alice", map.get("name"));
        assertEquals(30, map.get("age"));
    }

    @Test
    void nestedBeanToMapRecursion() {
        BeanSamples.Person p = new BeanSamples.Person();
        p.setName("Carol");
        p.setAge(40);
        BeanSamples.Address addr = new BeanSamples.Address("Shanghai", "200000");
        p.setAddress(addr);

        Map<?, ?> map = ConvertUtil.convert(Map.class, p);
        Object addressObj = map.get("address");
        assertInstanceOf(Map.class, addressObj);
        @SuppressWarnings("unchecked")
        Map<String, Object> addressMap = (Map<String, Object>) addressObj;
        assertEquals("Shanghai", addressMap.get("city"));
        assertEquals("200000", addressMap.get("zip"));
    }

    @Test
    void collectionOfBeansToMapRecursion() {
        BeanSamples.Person p = new BeanSamples.Person();
        p.setName("Dave");
        p.setAge(50);
        p.setAddresses(List.of(
                new BeanSamples.Address("A", "1"),
                new BeanSamples.Address("B", "2")));

        Map<?, ?> map = ConvertUtil.convert(Map.class, p);
        Object addressesObj = map.get("addresses");
        assertInstanceOf(List.class, addressesObj);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> addresses = (List<Map<String, Object>>) addressesObj;
        assertEquals(2, addresses.size());
        assertEquals("A", addresses.get(0).get("city"));
        assertEquals("B", addresses.get(1).get("city"));
    }

    @Test
    void recordToMap() {
        BeanSamples.Point point = new BeanSamples.Point(3, 4);
        Map<?, ?> map = ConvertUtil.convert(Map.class, point);
        assertEquals(3, map.get("x"));
        assertEquals(4, map.get("y"));
    }

    @Test
    void nonBeanSourceNotMatched() {
        // String -> Map：MapConverter 源匹配器拒绝，应“未找到”
        assertThrows(IllegalArgumentException.class,
                () -> ConvertUtil.convert(Map.class, "just a string"));
    }

    @Test
    void circularReferenceThrows() {
        BeanSamples.Person p = new BeanSamples.Person();
        p.setName("Loop");
        p.setAge(1);
        p.setSelf(p); // 自引用
        assertThrows(IllegalStateException.class,
                () -> ConvertUtil.convert(Map.class, p));
    }
}
