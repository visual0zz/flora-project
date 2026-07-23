package com.flora.codegen.engine;

import com.flora.codegen.engine.parser.Lson;
import com.flora.codegen.engine.parser.MetaParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证元数据解析器 {@link MetaParser} 的全部分支：
 * <ul>
 *   <li>{@code @Param} 参数解析（JSON 对象）</li>
 *   <li>{@code @Path} 输出路径解析（字符串字面量 vs 未加引号的标识符引用）</li>
 *   <li>{@code @Cartesian} 笛卡尔积声明解析</li>
 *   <li>{@code @Config} 配置解析</li>
 *   <li>无元数据时返回 null</li>
 *   <li>错误分支：未闭合花括号、非法 JSON、重复块类型</li>
 * </ul>
 */
class MetaParserTest {

    @Test
    void parsesParamAndPath() {
        String body = "@Param{ pkg: \"com.x\" }\n@Path{ \"Out.java\" }";
        MetaParser.MetaData data = MetaParser.parse(body, 1);

        assertEquals("com.x", data.param().get("pkg"));
        assertEquals("Out.java", data.pathValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void parsesCombine() {
        String body = "@Cartesian{ K: [A, B], V: [I, L] }";
        MetaParser.MetaData data = MetaParser.parse(body, 1);

        Map<String, Object> combine = data.cartesian();
        assertNotNull(combine);
        List<Object> kList = (List<Object>) combine.get("K");
        assertTrue(kList.get(0) instanceof Lson.Reference);
        assertEquals(2, kList.size());
    }

    @Test
    void noMetaReturnsNulls() {
        MetaParser.MetaData data = MetaParser.parse("just a comment", 1);
        assertNull(data.param());
        assertNull(data.cartesian());
        assertNull(data.pathValue());
        assertNull(data.config());
    }

    @Test
    void pathAsStringLiteral() {
        MetaParser.MetaData data = MetaParser.parse("@Path{ \"plain.txt\" }", 1);
        assertEquals("plain.txt", data.pathValue());
    }

    @Test
    void pathAsUnquotedIdentifier() {
        MetaParser.MetaData data = MetaParser.parse("@Path{ myRef }", 1);
        assertInstanceOf(Lson.Reference.class, data.pathValue());
    }

    @Test
    void unclosedBraceThrows() {
        CodeGenException ex = assertThrows(CodeGenException.class,
                () -> MetaParser.parse("@Param{ a: 1, b: {", 7));
        assertTrue(ex.getMessage().contains("闭合"), ex.getMessage());
    }

    @Test
    void invalidJsonThrows() {
        CodeGenException ex = assertThrows(CodeGenException.class,
                () -> MetaParser.parse("@Param{ ??? }", 3));
        assertTrue(ex.getMessage().contains("key"), ex.getMessage());
    }

    @Test
    void duplicateBlockTypeThrows() {
        CodeGenException ex = assertThrows(CodeGenException.class,
                () -> MetaParser.parse("@Param{ a: 1 } @Param{ b: 2 }", 1));
        assertTrue(ex.getMessage().contains("重复"), ex.getMessage());
    }

    @Test
    void parsesConfig() {
        MetaParser.MetaData data = MetaParser.parse("@Config{ autoWarning: false }", 1);
        assertNotNull(data.config());
        assertEquals(false, data.config().get("autoWarning"));
    }
}
