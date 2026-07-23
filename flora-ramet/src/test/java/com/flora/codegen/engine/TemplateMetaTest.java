package com.flora.codegen.engine;

import com.flora.codegen.engine.ast.MetaNode;
import com.flora.codegen.engine.ast.Node;
import com.flora.codegen.engine.parser.Lexer;
import com.flora.codegen.engine.parser.LsonNumber;
import com.flora.codegen.engine.parser.MetaParser;
import com.flora.codegen.engine.parser.Parser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证模板元数据的聚合视图 TemplateMeta 的边界：从 AST 提取 params/combine，
 * 以及笛卡尔积展开等模板级计算。
 */
class TemplateMetaTest {

    private TemplateMeta metaOf(String tpl) {
        List<Node> nodes = Parser.parse(Lexer.lex(tpl));
        List<Node> metaNodes = nodes.stream()
                .filter(n -> n instanceof MetaNode)
                .toList();
        if (metaNodes.isEmpty()) {
            return TemplateMeta.from(new MetaParser.MetaData(null, null, null, null, null));
        }
        return TemplateMeta.from(((MetaNode) metaNodes.get(0)).data());
    }

    @Test
    @SuppressWarnings("unchecked")
    void extractsParamsAndPath() throws IOException {
        TemplateMeta meta = metaOf("""
                <#meta>
                @Param{ pkg: "com.x", n: 3 }
                @Path{ "O.java" }
                </#meta>
                """);

        var variants = meta.expand();
        assertEquals(1, variants.size());
        assertEquals("O.java", variants.get(0).outputPath());
        assertEquals("com.x", variants.get(0).params().get("pkg"));
        assertEquals(LsonNumber.of(3), variants.get(0).params().get("n"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void extractsCombine() throws IOException {
        TemplateMeta meta = metaOf("""
                <#meta>
                @Param{ I: { name: "Int" } }
                @Cartesian{ K: [I] }
                @Path{ "O.java" }
                </#meta>
                """);

        // 通过 expand 验证轴值被正确解析到变体
        var variants = meta.expand();
        assertEquals(1, variants.size());
        Map<String, Object> params = variants.get(0).params();
        assertEquals("Int", ((Map<String, Object>) params.get("I")).get("name"));
        assertEquals("Int", ((Map<String, Object>) params.get("K")).get("name"));
    }

    @Test
    void cartesianProductExpandsAllAxes() {
        List<Map<String, Object>> combos = TemplateMeta.cartesian(
                List.of("K", "V"),
                List.of(List.of("I", "L"), List.of("I", "L")));

        assertEquals(4, combos.size());
        assertEquals("{K=I, V=I}", combos.get(0).toString());
        assertEquals("{K=L, V=L}", combos.get(3).toString());
    }

    @Test
    void describeComboFormatsReadably() {
        Map<String, Object> combo = Map.of("K", "Int", "V", "Long");
        assertEquals("K=Int, V=Long", TemplateMeta.describeCombo(List.of("K", "V"), combo));
    }

    @Test
    void noManifestYieldsEmptyExpand() throws IOException {
        TemplateMeta meta = metaOf("<#-- just a comment -->");
        assertTrue(meta.expand().isEmpty());
    }

    // ---- expand() 测试 ----

    @Test
    void expandWithoutCombineReturnsSingleVariant() throws IOException {
        TemplateMeta meta = metaOf("""
                <#meta>
                @Param{ n: 3 }
                @Path{ "O.java" }
                </#meta>
                """);
        List<TemplateMeta.Variant> variants = meta.expand();
        assertEquals(1, variants.size());
        assertEquals(LsonNumber.of(3), variants.get(0).params().get("n"));
        assertEquals("O.java", variants.get(0).outputPath());
    }

    @Test
    void expandWithCombineExpandsCartesian() throws IOException {
        TemplateMeta meta = metaOf("""
                <#meta>
                @Param{ I: { name: "Int" }, L: { name: "Long" } }
                @Cartesian{ K: [I, L], V: [I, L] }
                @Path{ "${K.name}-${V.name}.java" }
                </#meta>
                """);
        List<TemplateMeta.Variant> variants = meta.expand();
        assertEquals(4, variants.size());
        for (TemplateMeta.Variant v : variants) {
            Object k = v.params().get("K");
            Object vv = v.params().get("V");
            assertInstanceOf(Map.class, k);
            assertInstanceOf(Map.class, vv);
        }
        assertEquals("Int-Int.java", variants.get(0).outputPath());
        assertEquals("Long-Long.java", variants.get(3).outputPath());
    }

    @Test
    void expandMissingRefThrows() {
        // I 是引用但未定义 → RefResolver 在 expand 时抛出
        TemplateMeta meta = metaOf("""
                <#meta>
                @Cartesian{ K: [I] }
                @Path{ "O.java" }
                </#meta>
                """);
        assertThrows(CodeGenException.class, () -> meta.expand());
    }

    @Test
    void expandDuplicatePathMergesInCodeGenUtil() throws IOException {
        // 同路径不再在 expand() 层面抛异常，而是允许通过并在 CodeGenUtil 中合并
        TemplateMeta meta = metaOf("""
                <#meta>
                @Cartesian{ K: ["A", "B"] }
                @Path{ "same.java" }
                </#meta>
                """);
        // expand() 返回 2 个同路径 Variant（不再抛异常）
        List<TemplateMeta.Variant> variants = meta.expand();
        assertEquals(2, variants.size());
        assertEquals("same.java", variants.get(0).outputPath());
        assertEquals("same.java", variants.get(1).outputPath());
    }

    @Test
    void expandCaseInsensitivePathsPassThrough() throws IOException {
        // 大小写不同的路径在 expand() 层面不再拦截，由 Ramet 跨模板去重处理
        TemplateMeta meta = metaOf("""
                <#meta>
                @Cartesian{ K: ["a", "A"] }
                @Path{ "${K}.java" }
                </#meta>
                """);
        // expand() 返回 2 个 Variant，路径大小写不同但均不抛异常
        List<TemplateMeta.Variant> variants = meta.expand();
        assertEquals(2, variants.size());
        Set<String> paths = variants.stream().map(TemplateMeta.Variant::outputPath).collect(Collectors.toSet());
        assertTrue(paths.contains("a.java"));
        assertTrue(paths.contains("A.java"));
    }

    @Test
    void expandWithoutPathReturnsEmpty() throws IOException {
        TemplateMeta meta = metaOf("""
                <#meta>
                @Param{ n: 3 }
                </#meta>
                """);
        assertTrue(meta.expand().isEmpty());
    }

    @Test
    void exposesConfig() {
        TemplateMeta meta = metaOf("""
                <#meta>
                @Config{ autoWarning: false }
                @Path{ "O.java" }
                </#meta>
                """);
        assertNotNull(meta.config());
        assertEquals(false, meta.config().get("autoWarning"));
    }
}
