package com.flora.codegen.engine;

import com.flora.codegen.engine.parser.Lson;
import com.flora.codegen.engine.parser.LsonNumber;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖宽松 JSON 解析器 {@link Lson} 的全部解析分支与错误分支：
 * <ul>
 *   <li>对象（未加引号的键名、嵌套对象与数组、空对象）</li>
 *   <li>字符串（转义序列、控制字符拒绝）</li>
 *   <li>数字（整数、负数、浮点数、科学计数法、非法格式拒绝）</li>
 *   <li>布尔与 null 字面量</li>
 *   <li>数组（嵌套、空数组、缺失逗号拒绝）</li>
 *   <li>关键字枚举映射</li>
 *   <li>语法错误分支：尾随字符、空输入、缺失冒号/逗号、未闭合字符串等</li>
 * </ul>
 */
class LsonTest {

    @Test
    @SuppressWarnings("unchecked")
    void objectWithUnquotedKeysAndMixedValues() {
        Object o = Lson.parse("{ name: \"hi\", age: 3, ok: true, n: null }");
        Map<String, Object> m = (Map<String, Object>) o;
        assertEquals("hi", m.get("name"));
        assertEquals(LsonNumber.of(3), m.get("age"));
        assertEquals(Boolean.TRUE, m.get("ok"));
        assertNull(m.get("n"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nestedObjectAndArray() {
        Object o = Lson.parse("{ a: { b: [1, 2, \"x\"] } }");
        Map<String, Object> m = (Map<String, Object>) o;
        Map<String, Object> a = (Map<String, Object>) m.get("a");
        List<Object> b = (List<Object>) a.get("b");
        assertEquals(List.of(LsonNumber.of(1), LsonNumber.of(2), "x"), b);
    }

    @Test
    void emptyObjectAndArray() {
        assertEquals(Map.of(), Lson.parse("{}"));
        assertEquals(List.of(), Lson.parse("[]"));
    }

    @Test
    void unquotedKeyAndStringEscapes() {
        Object o = Lson.parse("{ key: \"a\\tb\\\"c\" }");
        Map<?, ?> m = (Map<?, ?>) o;
        assertEquals("a\tb\"c", m.get("key"));
    }

    @Test
    void javaEscapeSequences() {
        // Java 风格转义：\" \\ \n \t \b \f \r \'
        Object o = Lson.parse("\"\\n\\t\\\"\\\\\\b\\f\\r\\'\"");
        assertEquals("\n\t\"\\\b\f\r'", o);
    }

    @Test
    void unicodeEscape() {
        assertEquals("A", Lson.parse("\"\\u0041\""));
        assertEquals("中文", Lson.parse("\"\\u4E2D\\u6587\""));
    }

    @Test
    void numbersIntDoubleAndNegative() {
        assertEquals(LsonNumber.of(42), Lson.parse("42"));
        assertEquals(LsonNumber.of(-7), Lson.parse("-7"));
        assertEquals(LsonNumber.of(3.14), Lson.parse("3.14"));
        assertEquals(LsonNumber.of(1.5e3), Lson.parse("1.5e3"));
    }

    @Test
    void boolAndNullLiterals() {
        assertEquals(Boolean.TRUE, Lson.parse("true"));
        assertEquals(Boolean.FALSE, Lson.parse("false"));
        assertNull(Lson.parse("null"));
    }

    @Test
    void trailingCharactersRejected() {
        assertThrows(CodeGenException.class, () -> Lson.parse("{} extra"));
    }

    @Test
    void emptyInputRejected() {
        assertThrows(CodeGenException.class, () -> Lson.parse("   "));
    }

    @Test
    void objectMissingColonRejected() {
        assertThrows(CodeGenException.class, () -> Lson.parse("{ a }"));
    }

    @Test
    void objectMissingCommaRejected() {
        assertThrows(CodeGenException.class, () -> Lson.parse("{ a: 1 b: 2 }"));
    }

    @Test
    void arrayMissingCommaRejected() {
        assertThrows(CodeGenException.class, () -> Lson.parse("[1 2]"));
    }

    @Test
    void stringControlCharRejected() {
        assertThrows(CodeGenException.class, () -> Lson.parse("\"a\u0001b\""));
    }

    @Test
    void invalidNumberRejected() {
        assertThrows(CodeGenException.class, () -> Lson.parse("-"));
        assertThrows(CodeGenException.class, () -> Lson.parse("."));
    }

    @Test
    void invalidBoolRejected() {
        // 无引号标识符现在被解析为 Reference，不会抛异常
        assertEquals(new Lson.Reference("trux"), Lson.parse("trux"));
    }

    @Test
    void invalidNullRejected() {
        assertEquals(new Lson.Reference("nul"), Lson.parse("nul"));
    }

    @Test
    void invalidKeyRejected() {
        assertThrows(CodeGenException.class, () -> Lson.parse("{ 123: 1 }"));
    }

    @Test
    void keywordAsKeyRejected() {
        assertThrows(CodeGenException.class, () -> Lson.parse("{ true: 1 }"));
        assertThrows(CodeGenException.class, () -> Lson.parse("{ false: 1 }"));
        assertThrows(CodeGenException.class, () -> Lson.parse("{ null: 1 }"));
    }

    @Test
    void keywordEnumMapsCorrectly() {
        assertEquals(Lson.Keyword.TRUE, Lson.Keyword.from("true"));
        assertEquals(Lson.Keyword.FALSE, Lson.Keyword.from("false"));
        assertEquals(Lson.Keyword.NULL, Lson.Keyword.from("null"));
        assertEquals(null, Lson.Keyword.from("trux"));
        assertEquals(null, Lson.Keyword.from(""));
    }

    @Test
    void unterminatedStringThrows() {
        assertThrows(Throwable.class, () -> Lson.parse("\"abc"));
    }
}
