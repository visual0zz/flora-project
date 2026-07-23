package com.flora.codegen.engine.function;

import com.flora.codegen.LazyArg;
import com.flora.codegen.engine.runtime.FunctionRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证注册表中各内置函数的 name/arity/apply 行为。
 */
class FunctionTest {

    private static FunctionRegistry reg;

    @BeforeAll
    static void setup() {
        reg = new FunctionRegistry();
    }

    /** 将值包装为惰性参数列表。 */
    @SafeVarargs
    private static <T> List<LazyArg> lazy(T... values) {
        return Arrays.stream(values).map(v -> (LazyArg) () -> v).toList();
    }

    private static List<LazyArg> lazyList(List<?> list) {
        return list.stream().map(v -> (LazyArg) () -> v).toList();
    }

    @Test
    void capCapitalizesFirstLetter() {
        var fn = reg.get("capitalize");
        assertEquals("capitalize", fn.name());
        assertEquals(1, fn.arity());
        assertEquals("Foo", fn.apply(lazy("foo")));
        assertEquals("", fn.apply(lazy("")));
    }

    @Test
    void lowerAndUpper() {
        assertEquals("FOO", reg.get("uppercase").apply(lazy("Foo")));
        assertEquals("foo", reg.get("lowercase").apply(lazy("Foo")));
        List<Object> nullArg = new ArrayList<>();
        nullArg.add(null);
        assertNull(reg.get("uppercase").apply(lazyList(nullArg)));
    }

    @Test
    void javaStringEscapes() {
        var fn = reg.get("javaString");
        assertEquals("\"a\\nb\"", fn.apply(lazy("a\nb")));
        List<Object> nullArg = new ArrayList<>();
        nullArg.add(null);
        assertEquals("null", fn.apply(lazyList(nullArg)));
    }

    @Test
    void numberFormatFormatsNumbers() {
        var fn = reg.get("numberFormat");
        assertEquals("numberFormat", fn.name());
        assertEquals(2, fn.arity());
        assertEquals("3.14", fn.apply(lazy(3.14159, "0.00")));
        assertEquals("3.142", fn.apply(lazy(3.14159, "0.000")));
        assertEquals("1,024", fn.apply(lazy(1024, "#,###")));
        assertEquals("042", fn.apply(lazy(42, "000")));
    }

    @Test
    void containsChecksSubstring() {
        var fn = reg.get("contains");
        assertEquals("contains", fn.name());
        assertEquals(2, fn.arity());
        assertTrue((Boolean) fn.apply(lazy("hello world", "world")));
        assertEquals(Boolean.FALSE, fn.apply(lazy("hello", "xyz")));
        assertEquals(Boolean.FALSE, fn.apply(lazy(null, "x")));
        assertTrue((Boolean) fn.apply(lazy("abc", "")));
    }

    @Test
    void replaceAllOccurrences() {
        var fn = reg.get("replace");
        assertEquals("replace", fn.name());
        assertEquals(3, fn.arity());
        assertEquals("hello java world", fn.apply(lazy("hello foo world", "foo", "java")));
        assertEquals("abc", fn.apply(lazy("abc", "x", "y")));
        assertEquals("", fn.apply(lazy("", "a", "b")));
    }

    @Test
    void joinWithSeparator() {
        var fn = reg.get("join");
        assertEquals("join", fn.name());
        assertEquals(-1, fn.arity());
        assertEquals("a,b,c", fn.apply(lazy(",", "a", "b", "c")));
        assertEquals("a", fn.apply(lazy(",", "a")));
        assertEquals("1-2-3", fn.apply(lazy("-", 1, 2, 3)));
        assertThrows(IllegalArgumentException.class,
                () -> fn.apply(lazy(",")));
    }

    @Test
    void firstNonNull() {
        var fn = reg.get("firstNonNull");
        assertEquals("firstNonNull", fn.name());
        assertEquals(-1, fn.arity());
        assertEquals("hello", fn.apply(lazy("hello", "fallback")));
        assertEquals("fallback", fn.apply(lazy(null, "fallback")));
        assertEquals(42, fn.apply(lazy(42, 0)));
        assertEquals("second", fn.apply(lazy(null, "second", "third")));
    }

    @Test
    void lengthReturnsStringOrCollectionSize() {
        var fn = reg.get("length");
        assertEquals("length", fn.name());
        assertEquals(1, fn.arity());
        assertEquals(5, fn.apply(lazy("hello")));
        assertEquals(0, fn.apply(lazy("")));
        assertEquals(0, fn.apply(lazy((Object) null)));
        assertEquals(3, fn.apply(lazy(List.of(1, 2, 3))));
        assertEquals(0, fn.apply(lazy(List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> fn.apply(lazy(42)));
    }

    @Test
    void startsWithChecksPrefix() {
        var fn = reg.get("startsWith");
        assertEquals("startsWith", fn.name());
        assertEquals(2, fn.arity());
        assertTrue((Boolean) fn.apply(lazy("hello", "he")));
        assertEquals(Boolean.FALSE, fn.apply(lazy("hello", "xyz")));
        assertTrue((Boolean) fn.apply(lazy("hello", "")));
        assertEquals(Boolean.FALSE, fn.apply(lazy(null, "x")));
    }

    @Test
    void repeatRepeatsString() {
        var fn = reg.get("repeat");
        assertEquals("repeat", fn.name());
        assertEquals(2, fn.arity());
        assertEquals("abcabcabc", fn.apply(lazy("abc", 3)));
        assertEquals("", fn.apply(lazy("abc", 0)));
        assertEquals("", fn.apply(lazy("", 5)));
    }

    @Test
    void andOrNot() {
        var andFn = reg.get("and");
        assertEquals("and", andFn.name());
        assertEquals(-1, andFn.arity());
        assertTrue((Boolean) andFn.apply(lazy(true, true)));
        assertFalse((Boolean) andFn.apply(lazy(true, false)));
        assertFalse((Boolean) andFn.apply(lazy(true, null)));

        var orFn = reg.get("or");
        assertEquals("or", orFn.name());
        assertEquals(-1, orFn.arity());
        assertTrue((Boolean) orFn.apply(lazy(true, false)));
        assertFalse((Boolean) orFn.apply(lazy(false, false)));

        var notFn = reg.get("not");
        assertEquals("not", notFn.name());
        assertEquals(1, notFn.arity());
        assertFalse((Boolean) notFn.apply(lazy(true)));
        assertTrue((Boolean) notFn.apply(lazy(false)));
    }

    @Test
    void andOrVariadic() {
        var andFn = reg.get("and");
        // 变参 and：所有为 true 才 true
        assertTrue((Boolean) andFn.apply(lazy(true, true, true)));
        assertFalse((Boolean) andFn.apply(lazy(true, false, true)));
        assertFalse((Boolean) andFn.apply(lazy(false, true, true)));

        var orFn = reg.get("or");
        // 变参 or：有一个 true 就 true
        assertTrue((Boolean) orFn.apply(lazy(false, false, true)));
        assertTrue((Boolean) orFn.apply(lazy(true, false, false)));
        assertFalse((Boolean) orFn.apply(lazy(false, false, false)));
    }

    @Test
    void equalsAny() {
        var fn = reg.get("equalsAny");
        assertEquals("equalsAny", fn.name());
        assertEquals(-1, fn.arity());
        // 匹配第一个
        assertTrue((Boolean) fn.apply(lazy("hello", "hello", "world")));
        // 匹配中间
        assertTrue((Boolean) fn.apply(lazy("world", "hello", "world")));
        // 不匹配
        assertFalse((Boolean) fn.apply(lazy("x", "hello", "world")));
        // 数字匹配
        assertTrue((Boolean) fn.apply(lazy(42, 1, 42, 3)));
        // null 匹配
        assertTrue((Boolean) fn.apply(lazy(null, "a", null)));
        // null 不匹配任何
        assertFalse((Boolean) fn.apply(lazy(null, "a", "b")));
        // 单参数（只有 target，没有 candidates）→ false
        assertFalse((Boolean) fn.apply(lazy("x")));
    }

    @Test
    void greaterThanAndLessThan() {
        var gt = reg.get("greaterThan");
        assertEquals("greaterThan", gt.name());
        assertEquals(2, gt.arity());
        assertTrue((Boolean) gt.apply(lazy(3, 2)));
        assertFalse((Boolean) gt.apply(lazy(2, 2)));

        var lt = reg.get("lessThan");
        assertEquals("lessThan", lt.name());
        assertEquals(2, lt.arity());
        assertTrue((Boolean) lt.apply(lazy(2, 3)));
        assertFalse((Boolean) lt.apply(lazy(3, 3)));
    }

    @Test
    void equalsAndNotEquals() {
        var eq = reg.get("equals");
        assertEquals("equals", eq.name());
        assertEquals(2, eq.arity());
        assertTrue((Boolean) eq.apply(lazy(1, 1.0)));
        assertFalse((Boolean) eq.apply(lazy(1, 2)));
        assertFalse((Boolean) eq.apply(lazy(null, 1)));

        var neq = reg.get("notEquals");
        assertEquals("notEquals", neq.name());
        assertEquals(2, neq.arity());
        assertTrue((Boolean) neq.apply(lazy(1, 2)));
        assertFalse((Boolean) neq.apply(lazy(1, 1.0)));
    }

    @Test
    void nullChecks() {
        var isNull = reg.get("isNull");
        assertEquals("isNull", isNull.name());
        assertEquals(1, isNull.arity());
        assertTrue((Boolean) isNull.apply(lazy((Object) null)));
        assertFalse((Boolean) isNull.apply(lazy("")));

        var notNull = reg.get("notNull");
        assertEquals("notNull", notNull.name());
        assertEquals(1, notNull.arity());
        assertFalse((Boolean) notNull.apply(lazy((Object) null)));
        assertTrue((Boolean) notNull.apply(lazy("")));
    }

    @Test
    void isEmptyAndIsBlank() {
        var isEmpty = reg.get("isEmpty");
        assertTrue((Boolean) isEmpty.apply(lazy("")));
        assertFalse((Boolean) isEmpty.apply(lazy(" ")));
        assertTrue((Boolean) isEmpty.apply(lazy((Object) null)));

        var isBlank = reg.get("isBlank");
        assertTrue((Boolean) isBlank.apply(lazy(" ")));
        assertTrue((Boolean) isBlank.apply(lazy("")));
        assertTrue((Boolean) isBlank.apply(lazy((Object) null)));
    }

    @Test
    void concatCombinesStrings() {
        var fn = reg.get("concat");
        assertEquals("concat", fn.name());
        assertEquals(-1, fn.arity());
        assertEquals("abc", fn.apply(lazy("a", "b", "c")));
        assertEquals("a", fn.apply(lazy("a")));
        assertEquals("", fn.apply(lazy()));
    }

    @Test
    void rangeGeneratesSequence() {
        var fn = reg.get("range");
        assertEquals("range", fn.name());
        assertEquals(2, fn.arity());
        assertEquals(List.of(2L, 3L, 4L, 5L), fn.apply(lazy(2, 5)));
        assertEquals(List.of(5L), fn.apply(lazy(5, 5)));
    }

    @Test
    void seqJoinGeneratesSequenceString() {
        var fn = reg.get("sequenceJoin");
        assertEquals("sequenceJoin", fn.name());
        assertEquals(4, fn.arity());
        assertEquals("T1,T2,T3", fn.apply(lazy("T{}", 1, 3, ",")));
        assertEquals("x,x,x", fn.apply(lazy("x", 1, 3, ",")));
    }

    @Test
    void lengthOnStringAndCollection() {
        var fn = reg.get("length");
        assertEquals(3, fn.apply(lazy("abc")));
        assertEquals(2, fn.apply(lazy(List.of(1, 2))));
    }

    @Test
    void lengthOnArray() {
        var fn = reg.get("length");
        assertEquals(3, fn.apply(lazy((Object) new String[]{"a", "b", "c"})));
        assertEquals(4, fn.apply(lazy((Object) new int[]{1, 2, 3, 4})));
        assertEquals(2, fn.apply(lazy((Object) new long[]{10L, 20L})));
        assertEquals(0, fn.apply(lazy((Object) new Object[]{})));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sortByOrdersByField() {
        var fn = reg.get("sortBy");
        List<Object> items = List.of(
                Map.of("name", "Charlie", "age", 30),
                Map.of("name", "Alice", "age", 25),
                Map.of("name", "Bob", "age", 20));
        List<Object> result = (List<Object>) fn.apply(lazy(items, "age"));
        assertEquals("Bob", ((Map<String, Object>) result.get(0)).get("name"));
        assertEquals("Alice", ((Map<String, Object>) result.get(1)).get("name"));
        assertEquals("Charlie", ((Map<String, Object>) result.get(2)).get("name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sortByStringField() {
        var fn = reg.get("sortBy");
        List<Object> items = List.of(
                Map.of("letter", "Z"),
                Map.of("letter", "A"),
                Map.of("letter", "M"));
        List<Object> result = (List<Object>) fn.apply(lazy(items, "letter"));
        assertEquals("A", ((Map<String, Object>) result.get(0)).get("letter"));
        assertEquals("M", ((Map<String, Object>) result.get(1)).get("letter"));
        assertEquals("Z", ((Map<String, Object>) result.get(2)).get("letter"));
    }

    @Test
    void sortByPreservesSize() {
        var fn = reg.get("sortBy");
        assertEquals(0, ((List<?>) fn.apply(lazy(List.of(), "x"))).size());
        assertEquals(1, ((List<?>) fn.apply(lazy(List.of(Map.of("a", 1)), "a"))).size());
    }
}
