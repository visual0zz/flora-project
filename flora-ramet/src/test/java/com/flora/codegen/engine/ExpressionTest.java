package com.flora.codegen.engine;

import com.flora.codegen.engine.parser.Lson;
import com.flora.codegen.engine.parser.LsonNumber;
import com.flora.codegen.engine.runtime.Context;
import com.flora.codegen.engine.runtime.RefResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证表达式求值（Lson + RefResolver）的边界：
 * 字面量、变量、函数调用、指令表达式简写。
 *
 * <p>在 {@code ${}} 上下文，函数调用直接写 {@code name(args)}。
 * 在 parseDirectiveExpr（{@code &lt;#if&gt;} 上下文）中，使用文本函数简写。
 */
class ExpressionTest {

    private Context ctx(Map<String, Object> params) {
        return Context.of(params, Map.of());
    }

    /** 模拟 ${expr} 上下文求值。 */
    private Object eval(String src) {
        return RefResolver.evalCtx(Lson.parse(src), ctx(Map.of()));
    }

    private Object eval(String src, Map<String, Object> params) {
        return RefResolver.evalCtx(Lson.parse(src), ctx(params));
    }

    /** 模拟 &lt;#if expr&gt; 上下文求值（指令表达式简写）。 */
    private Object evalDir(String src) {
        return RefResolver.evalCtx(Lson.parse(src, -1), ctx(Map.of()));
    }

    private Object evalDir(String src, Map<String, Object> params) {
        return RefResolver.evalCtx(Lson.parse(src, -1), ctx(params));
    }

    // ========== parseExpr 测试：字面量、变量、name() 调用 ==========

    @Test
    void numericLiteral() {
        assertEquals(LsonNumber.of(42), eval("42"));
    }

    @Test
    void stringLiteral() {
        assertEquals("abc", eval("\"abc\""));
    }

    @Test
    void variableLookupFromParams() {
        assertEquals(5, eval("x", Map.of("x", 5)));
    }

    @Test
    void propertyChainOnMap() {
        Map<String, Object> inner = Map.of("name", "Int");
        Object v = eval("k.name", Map.of("k", inner));
        assertEquals("Int", v);
    }

    @Test
    void functionCallInvoked() {
        assertEquals("Foo", eval("capitalize(\"foo\")"));
    }



    @Test
    void unknownFunctionThrows() {
        assertThrows(CodeGenException.class,
                () -> eval("nope(\"x\")"));
    }

    @Test
    void arityMismatchThrows() {
        CodeGenException ex = assertThrows(CodeGenException.class,
                () -> eval("capitalize(\"a\", \"b\")"));
        assertTrue(ex.getMessage().contains("参数"), ex.getMessage());
    }

    @Test
    void doubleLiteral() {
        assertEquals(LsonNumber.of(3.14), eval("3.14"));
    }

    @Test
    void stringLiteralPlain() {
        assertEquals("hello", eval("\"hello\""));
    }

    @Test
    void escapeSequencesInString() {
        assertEquals("a\tb", eval("\"a\\tb\""));
        assertEquals("\"q\"", eval("\"\\\"q\\\"\""));
    }

    @Test
    void funcCallWithVariableArgument() {
        assertEquals("Foo", eval("capitalize(x)", Map.of("x", "foo")));
    }

    @Test
    void nestedFunctionCall() {
        assertEquals("X", eval("capitalize(uppercase(\"x\"))"));
    }

    @Test
    void emptyGroupingThrows() {
        assertThrows(CodeGenException.class, () -> Lson.parse("()"));
    }

    // ========== 指令表达式简写测试：parseDirectiveExpr ==========

    @Test
    void dirGreaterThan() {
        assertTrue((Boolean) evalDir("2 greaterThan 1"));
        assertEquals(Boolean.FALSE, evalDir("1 greaterThan 2"));
        assertEquals(Boolean.FALSE, evalDir("2 greaterThan 2"));
    }

    @Test
    void dirLessThan() {
        assertTrue((Boolean) evalDir("1 lessThan 2"));
        assertEquals(Boolean.FALSE, evalDir("2 lessThan 1"));
        assertEquals(Boolean.FALSE, evalDir("2 lessThan 2"));
    }

    @Test
    void dirGreaterThanOrEquals() {
        assertTrue((Boolean) evalDir("2 greaterThanOrEquals 1"));
        assertTrue((Boolean) evalDir("2 greaterThanOrEquals 2"));
        assertEquals(Boolean.FALSE, evalDir("1 greaterThanOrEquals 2"));
    }

    @Test
    void dirLessThanOrEquals() {
        assertTrue((Boolean) evalDir("1 lessThanOrEquals 2"));
        assertTrue((Boolean) evalDir("2 lessThanOrEquals 2"));
        assertEquals(Boolean.FALSE, evalDir("3 lessThanOrEquals 2"));
    }

    @Test
    void dirEquals() {
        assertTrue((Boolean) evalDir("1 equals 1"));
        assertTrue((Boolean) evalDir("\"a\" equals \"a\""));
        assertEquals(Boolean.FALSE, evalDir("\"a\" equals \"b\""));
    }

    @Test
    void dirNotEquals() {
        assertTrue((Boolean) evalDir("1 notEquals 2"));
        assertEquals(Boolean.FALSE, evalDir("1 notEquals 1"));
    }

    @Test
    void dirAnd() {
        assertTrue((Boolean) evalDir("true and true"));
        assertEquals(Boolean.FALSE, evalDir("true and false"));
        assertEquals(Boolean.FALSE, evalDir("false and true"));
    }

    @Test
    void dirOr() {
        assertTrue((Boolean) evalDir("true or false"));
        assertTrue((Boolean) evalDir("false or true"));
        assertEquals(Boolean.FALSE, evalDir("false or false"));
    }

    @Test
    void dirNot() {
        assertEquals(Boolean.TRUE, evalDir("not false"));
        assertEquals(Boolean.FALSE, evalDir("not true"));
    }

    @Test
    void dirLeftToRightNoPrecedence() {
        // a greaterThan b and c greaterThan d → greaterThan(and(greaterThan(a,b),c),d) 无优先级，严格左结合
        assertEquals(Boolean.FALSE, evalDir("2 greaterThan 1 and 0 greaterThan 1", Map.of()));
    }

    @Test
    void dirParenthesizedGrouping() {
        // (2 greaterThan 1) and (0 greaterThan 1) → and(greaterThan(2,1), greaterThan(0,1)) → false
        assertEquals(Boolean.FALSE, evalDir("(2 greaterThan 1) and (0 greaterThan 1)"));
    }

    @Test
    void dirParensOverrideLeftToRight() {
        // (2 greaterThan 1) and (1 greaterThan 0) → true
        assertTrue((Boolean) evalDir("(2 greaterThan 1) and (1 greaterThan 0)"));
    }

    @Test
    void dirNotWithComparison() {
        // not (1 greaterThan 2) → not(false) → true
        assertEquals(Boolean.TRUE, evalDir("not (1 greaterThan 2)"));
        // not true equals false → left-to-right: equals(not(true), false) → equals(false, false) → true
        assertEquals(Boolean.TRUE, evalDir("not true equals false"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dirRange() {
        List<Long> r = (List<Long>) evalDir("range(1, 3)");
        assertEquals(List.of(1L, 2L, 3L), r);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dirRangeFromVariable() {
        List<Long> r = (List<Long>) evalDir("range(1, n)", Map.of("n", 5L));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), r);
    }

    @Test
    void dirRangeNonNumberThrows() {
        assertThrows(CodeGenException.class,
                () -> evalDir("range(1, \"x\")"));
    }

    @Test
    void dirCompareNonNumberThrows() {
        assertThrows(CodeGenException.class,
                () -> evalDir("1 lessThan \"x\""));
    }

    @Test
    void dirUnknownFunctionThrows() {
        assertThrows(CodeGenException.class,
                () -> Lson.parse("a xyz b", -1));
    }

    @Test
    void dirDollarCallNotSupported() {
        // 指令表达式中不支持 name() 调用
        // $greaterThan 不作为函数名识别，(1, 2) 会被当作多余字符
        assertThrows(CodeGenException.class,
                () -> evalDir("$greaterThan(1, 2)"));
    }

    // ========== 索引访问测试 ==========

    @Test
    void indexAccessOnList() {
        assertEquals("b", eval("arr[1]", Map.of("arr", List.of("a", "b", "c"))));
    }

    @Test
    void indexAccessOnMap() {
        assertEquals("x", eval("m[\"k\"]", Map.of("m", Map.of("k", "x"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void indexAccessOnArray() {
        assertEquals(3, eval("a[2]", Map.of("a", (Object) new int[]{1, 2, 3, 4})));
        assertEquals("b", eval("a[1]", Map.of("a", (Object) new String[]{"a", "b", "c"})));
    }

    @Test
    void indexAccessChained() {
        // items[0].name → 先索引取第一个 Map，再属性访问
        Map<String, Object> item = Map.of("name", "Foo");
        assertEquals("Foo", eval("items[0].name", Map.of("items", List.of(item))));
    }

    @Test
    void indexAccessInDirectiveExpr() {
        // 指令表达式中也支持索引语法
        assertEquals(Boolean.TRUE, evalDir("arr[0] equals 1",
                Map.of("arr", List.of(1, 2, 3))));
    }

    @Test
    void indexAccessWithVariableIndex() {
        assertEquals("c", eval("arr[i]", Map.of("arr", List.of("a", "b", "c"), "i", 2)));
    }
}
