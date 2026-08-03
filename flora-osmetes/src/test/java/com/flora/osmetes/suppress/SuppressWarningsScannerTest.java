package com.flora.osmetes.suppress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SuppressWarningsScanner} 的作用域解析测试。
 */
class SuppressWarningsScannerTest {

    private static SuppressWarningsScanner scan(String text) {
        return SuppressWarningsScanner.parse(text);
    }

    @Test
    void classLevelSuppressesWholeTypeBody() {
        SuppressWarningsScanner s = scan(
                "@SuppressWarnings(\"osmetes:tab\")\n"
                        + "class Foo {\n"
                        + "    int x;\n"
                        + "}\n"
                        + "class Bar {\n"
                        + "    int y;\n"
                        + "}\n");
        assertTrue(s.isSuppressed(1, "tab"));
        assertTrue(s.isSuppressed(2, "tab"));
        assertTrue(s.isSuppressed(3, "tab"));
        assertTrue(s.isSuppressed(4, "tab"));
        assertFalse(s.isSuppressed(5, "tab"), "Bar 不应被 Foo 的注解抑制");
        assertFalse(s.isSuppressed(6, "tab"));
    }

    @Test
    void methodLevelSuppressesOnlyThatMethod() {
        SuppressWarningsScanner s = scan(
                "class Foo {\n"
                        + "    @SuppressWarnings(\"osmetes:secret\")\n"
                        + "    void m() {\n"
                        + "        String a = \"x\";\n"
                        + "    }\n"
                        + "    void n() {\n"
                        + "        String b = \"y\";\n"
                        + "    }\n"
                        + "}\n");
        assertTrue(s.isSuppressed(2, "secret"));
        assertTrue(s.isSuppressed(3, "secret"));
        assertTrue(s.isSuppressed(4, "secret"));
        assertFalse(s.isSuppressed(6, "secret"), "n 方法不应被 m 的注解抑制");
    }

    @Test
    void statementLevelEndsAtSemicolon() {
        SuppressWarningsScanner s = scan(
                "class Foo {\n"
                        + "    void m() {\n"
                        + "        @SuppressWarnings(\"osmetes:tab\")\n"
                        + "        int x = 1;\n"
                        + "        int y = 2;\n"
                        + "    }\n"
                        + "}\n");
        assertTrue(s.isSuppressed(3, "tab"));
        assertTrue(s.isSuppressed(4, "tab"));
        assertFalse(s.isSuppressed(5, "tab"), "后续语句不应被抑制");
    }

    @Test
    void multipleNamesInArrayValue() {
        SuppressWarningsScanner s = scan(
                "@SuppressWarnings({\"osmetes:tab\", \"osmetes:secret\"})\n"
                        + "class Foo {\n"
                        + "}\n");
        assertTrue(s.isSuppressed(1, "tab"));
        assertTrue(s.isSuppressed(1, "secret"));
        assertFalse(s.isSuppressed(1, "whitetail"));
    }

    @Test
    void namedValueElement() {
        SuppressWarningsScanner s = scan(
                "@SuppressWarnings(value = \"osmetes:tab\")\n"
                        + "class Foo {\n"
                        + "}\n");
        assertTrue(s.isSuppressed(1, "tab"));
    }

    @Test
    void nonOsmetesValueIgnored() {
        SuppressWarningsScanner s = scan(
                "@SuppressWarnings(\"deprecation\")\n"
                        + "class Foo {\n"
                        + "}\n");
        assertFalse(s.isSuppressed(1, "tab"));
        assertFalse(s.isSuppressed(1, "deprecation"), "非 osmetes 前缀不应产生任何抑制");
    }

    @Test
    void annotationInParensSuppressesOnlyItsLine() {
        SuppressWarningsScanner s = scan(
                "class Foo {\n"
                        + "    void m(@SuppressWarnings(\"osmetes:tab\") String s) {\n"
                        + "        int x = 1;\n"
                        + "    }\n"
                        + "}\n");
        assertTrue(s.isSuppressed(2, "tab"));
        assertFalse(s.isSuppressed(3, "tab"), "参数注解不应抑制方法体");
    }

    @Test
    void arrayValueBraceInsideAnnotationDoesNotConfuseScope() {
        SuppressWarningsScanner s = scan(
                "@SuppressWarnings({\"osmetes:tab\"})\n"
                        + "class Foo {\n"
                        + "    int x;\n"
                        + "}\n");
        assertTrue(s.isSuppressed(3, "tab"), "类体应被覆盖，而非被注解数组花括号误导");
    }

    @Test
    void annotationsInCommentsAndStringsIgnored() {
        SuppressWarningsScanner s = scan(
                "// @SuppressWarnings(\"osmetes:tab\")\n"
                        + "String s = \"@SuppressWarnings(\\\"osmetes:tab\\\")\";\n"
                        + "class Foo {\n"
                        + "    int x;\n"
                        + "}\n");
        assertFalse(s.isSuppressed(1, "tab"), "注释中的注解不应生效");
        assertFalse(s.isSuppressed(2, "tab"), "字符串中的注解不应生效");
        assertFalse(s.isSuppressed(3, "tab"));
    }

    @Test
    void annotationFollowedByOtherAnnotations() {
        SuppressWarningsScanner s = scan(
                "@SuppressWarnings(\"osmetes:tab\")\n"
                        + "@Deprecated\n"
                        + "@SuppressWarnings(value = {\"osmetes:secret\"})\n"
                        + "class Foo {\n"
                        + "    String p = \"x\";\n"
                        + "}\n");
        assertTrue(s.isSuppressed(1, "tab"));
        assertTrue(s.isSuppressed(2, "tab"), "夹在中间的注解行仍属 Foo 类体范围");
        assertTrue(s.isSuppressed(3, "secret"));
        assertTrue(s.isSuppressed(5, "tab"));
        assertTrue(s.isSuppressed(5, "secret"));
    }

    @Test
    void textBlockContentIgnored() {
        SuppressWarningsScanner s = scan(
                "class Foo {\n"
                        + "    String t = \"\"\"\n"
                        + "        @SuppressWarnings(\"osmetes:tab\") {\n"
                        + "        }\n"
                        + "        \"\"\";\n"
                        + "    int x;\n"
                        + "}\n");
        assertFalse(s.isSuppressed(2, "tab"), "文本块内容中的注解不应生效");
        assertFalse(s.isSuppressed(3, "tab"));
        assertFalse(s.isSuppressed(4, "tab"));
    }

    @Test
    void realisticSnippetParsesWithoutError() {
        // 泛型、lambda、record、嵌套注解与数组初始化混排，验证解析不崩溃且作用域正确
        SuppressWarningsScanner s = scan(
                "package demo;\n"
                        + "import java.util.List;\n"
                        + "record Point(int x, int y) {}\n"
                        + "@SuppressWarnings(\"osmetes:tab\")\n"
                        + "public class Service<T extends Comparable<T>> {\n"
                        + "    private final List<T> items = List.of();\n"
                        + "    @Deprecated\n"
                        + "    @SuppressWarnings({\"osmetes:secret\", \"osmetes:whitetail\"})\n"
                        + "    public T pick(@SuppressWarnings(\"osmetes:tab\") int index) {\n"
                        + "        return items.get(index);\n"
                        + "    }\n"
                        + "    Runnable r = () -> { System.out.println(\"a = b\"); };\n"
                        + "}\n");
        assertTrue(s.isSuppressed(4, "tab"), "类级注解覆盖整个类体");
        assertTrue(s.isSuppressed(10, "tab"));
        assertTrue(s.isSuppressed(9, "secret"));
        assertTrue(s.isSuppressed(10, "whitetail"), "方法级 whitetail 注解覆盖方法体");
        assertTrue(s.isSuppressed(9, "tab"), "参数注解仅注解行");
        assertTrue(s.isSuppressed(11, "tab"), "方法闭合括号行仍在类注解覆盖范围内");
        assertFalse(s.isSuppressed(12, "secret"), "方法注解不应覆盖方法体之外的行");
    }
}
