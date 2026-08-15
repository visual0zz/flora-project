package com.flora.root.container.either;

import com.flora.root.container.Variant;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariantTest {

    private static Variant intStringDouble() {
        return Variant.of(Integer.class, String.class, Double.class);
    }

    // ── 工厂与校验 ──

    @Test
    void ofTypesCreatesValueless() {
        Variant v = intStringDouble();
        assertTrue(v.isValueless());
        assertEquals(-1, v.index());
        assertNull(v.currentType());
        assertEquals(3, v.size());
    }

    @Test
    void ofValueCreatesFilled() {
        Variant v = Variant.of(42, Integer.class, String.class);
        assertEquals(0, v.index());
        assertEquals(42, v.value());
        assertTrue(v.holds(Integer.class));
    }

    @Test
    void invalidTypeTablesRejected() {
        // CheckUtil.notNull 对 null 抛 NullPointerException
        assertThrows(IllegalArgumentException.class, () -> Variant.of());
        assertThrows(NullPointerException.class, () -> Variant.of((Class<?>[]) null));
        assertThrows(NullPointerException.class, () -> Variant.of(Integer.class, null));
        assertThrows(IllegalArgumentException.class, () -> Variant.of(Integer.class, Integer.class));
    }

    // ── 状态查询 ──

    @Test
    void stateQueries() {
        Variant v = intStringDouble().set("你好");
        assertEquals(1, v.index());
        assertEquals(String.class, v.currentType());
        assertTrue(v.holds(String.class));
        assertFalse(v.holds(Integer.class));
        assertTrue(v.holdsIndex(1));
        assertFalse(v.isValueless());
    }

    // ── 设置 / 清除 ──

    @Test
    void setAutoMatchesType() {
        assertEquals(1, intStringDouble().set("hi").index());
        assertEquals(2, intStringDouble().set(3.14).index());
        assertEquals(0, intStringDouble().set(7).index());
    }

    @Test
    void setAutoMatchIsImmutable() {
        Variant v1 = intStringDouble().set(1);
        Variant v2 = v1.set("x");
        assertEquals(1, v1.value());
        assertEquals("x", v2.value());
        assertTrue(v1.holds(Integer.class));
        assertTrue(v2.holds(String.class));
    }

    @Test
    void setByIndexAndType() {
        assertEquals("v", intStringDouble().set(1, "v").value());
        assertEquals(1, intStringDouble().set(String.class, "v").index());
        assertEquals(2.5, (Double) intStringDouble().set(2, 2.5).value());
    }

    @Test
    void clearMakesValueless() {
        Variant v = intStringDouble().set(5).clear();
        assertTrue(v.isValueless());
        assertThrows(NoSuchElementException.class, v::value);
    }

    @Test
    void nullValueRequiresExplicitType() {
        // set(Object) 对 null 抛 NullPointerException（自动匹配无法推断类型）
        assertThrows(NullPointerException.class, () -> intStringDouble().set(null));
        Variant v = intStringDouble().set(String.class, null);
        assertTrue(v.holds(String.class));
        assertEquals(Optional.empty(), v.get(String.class));
        assertNull(v.value());
    }

    @Test
    void mismatchedSetRejected() {
        Variant v = intStringDouble();
        // 索引 1 声明为 String，传 Integer 值应被拒绝
        assertThrows(IllegalArgumentException.class, () -> v.set(1, 123));
        assertThrows(IllegalArgumentException.class, () -> v.set(Long.class, 1L));
        assertThrows(IllegalArgumentException.class, () -> v.set(9, "越界"));
        assertThrows(IllegalArgumentException.class, () -> v.set(new Object()));
    }

    // ── 读取 ──

    @Test
    void getReturnsTypedValue() {
        Variant v = intStringDouble().set(9);
        assertEquals(Optional.of(9), v.get(Integer.class));
        assertEquals(Optional.empty(), v.get(String.class));
        assertEquals(9, v.getOrElse(Integer.class, -1));
        assertEquals("缺省", v.getOrElse(String.class, "缺省"));
        assertEquals(9, v.getValue(Integer.class));
        assertThrows(IllegalArgumentException.class, () -> v.getValue(String.class));
    }

    @Test
    void valueThrowsWhenValueless() {
        Variant v = intStringDouble();
        assertThrows(NoSuchElementException.class, v::value);
        assertThrows(NoSuchElementException.class, () -> v.visit(o -> "x"));
    }

    // ── visit / 模式匹配 ──

    @Test
    void visitSingleVisitorAppliesToValue() {
        Variant v = intStringDouble().set(3);
        assertEquals(3, v.visit(Object::hashCode).intValue());
    }

    @Test
    void visitDispatchesByIndex() {
        Variant intV = intStringDouble().set(3);
        Variant strV = intStringDouble().set("abc");
        assertEquals("0:3", intV.visit(
                o -> "0:" + o, o -> "1:" + o, o -> "2:" + o));
        assertEquals("1:abc", strV.visit(
                o -> "0:" + o, o -> "1:" + o, o -> "2:" + o));
    }

    @Test
    void visitWrongArityRejected() {
        Variant v = intStringDouble().set(1);
        // 类型表有 3 个替代类型，访问器数组长度不足时应被拒绝（单访问器调用是合法重载）
        java.util.function.Function<Object, Object>[] tooFew =
                new java.util.function.Function[]{o -> "x"};
        assertThrows(IllegalArgumentException.class, () -> v.visit(tooFew));
    }

    // ── JDK 集成 ──

    @Test
    void streamYieldsCurrentValueOnly() {
        assertEquals(List.of(5), intStringDouble().set(5).stream().toList());
        assertEquals(0, intStringDouble().stream().count());
    }

    @Test
    void multiTypeDispatchWorksAcrossAllAlternatives() {
        Variant a = Variant.of(Integer.class, String.class).set(1);
        Variant b = Variant.of(Integer.class, String.class).set("s");
        Integer inc = a.visit(o -> ((Integer) o) + 1);
        String exclaim = b.visit(o -> ((String) o) + "!");
        assertEquals(2, inc);
        assertEquals("s!", exclaim);
    }

    // ── 值语义 ──

    @Test
    void valueSemantics() {
        assertEquals(intStringDouble().set(1), intStringDouble().set(1));
        assertFalse(intStringDouble().set(1).equals(intStringDouble().set("1")));
        assertEquals(intStringDouble().set(1).hashCode(), intStringDouble().set(1).hashCode());
        assertTrue(intStringDouble().set(1).toString().contains("Integer"));
    }
}
