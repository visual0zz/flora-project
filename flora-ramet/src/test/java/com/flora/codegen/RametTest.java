package com.flora.codegen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.flora.codegen.engine.CodeGenException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖 CLI 入口 {@link Ramet} 的集成测试：
 * <ul>
 *   <li>单模板生成</li>
 *   <li>dry-run 模式</li>
 *   <li>include 模板解析</li>
 *   <li>目录不存在异常</li>
 * </ul>
 */
class RametTest {

    @Test
    void generatesSingleTemplate(@TempDir Path tmpDir) throws IOException {
        Path tplDir = tmpDir.resolve("templates");
        Files.createDirectories(tplDir);
        Path outDir = tmpDir.resolve("out");

        String tpl = """
                <#meta>
                @Param{ pkg: "com.x", name: "Foo" }
                @Path{ "Foo.java" }
                </#meta>
                package ${pkg};
                public class ${name} {}
                """;
        Files.writeString(tplDir.resolve("test.ramet"), tpl, StandardCharsets.UTF_8);

        Ramet.run(tplDir, outDir, false);

        Path generated = outDir.resolve("Foo.java");
        assertTrue(Files.exists(generated), "输出文件应存在");
        String content = Files.readString(generated, StandardCharsets.UTF_8);
        assertAll(
                () -> assertTrue(content.contains("package com.x;")),
                () -> assertTrue(content.contains("public class Foo {}"))
        );
    }

    @Test
    void dryRunDoesNotCreateOutput(@TempDir Path tmpDir) throws IOException {
        Path tplDir = tmpDir.resolve("templates");
        Files.createDirectories(tplDir);
        Path outDir = tmpDir.resolve("out");

        String tpl = "<#meta>@Path{ \"D.java\" }</#meta>body";
        Files.writeString(tplDir.resolve("d.ramet"), tpl, StandardCharsets.UTF_8);

        Ramet.run(tplDir, outDir, true);

        assertFalse(Files.exists(outDir), "dry-run 模式下不应写入文件");
    }

    @Test
    void resolvesIncludes(@TempDir Path tmpDir) throws IOException {
        Path tplDir = tmpDir.resolve("templates");
        Files.createDirectories(tplDir);
        Path outDir = tmpDir.resolve("out");

        String included = "<#meta>@Path{ \"inc.java\" }</#meta>[${x}]";
        String host = """
                <#meta>
                @Param{ x: "hello" }
                @Path{ "host.java" }
                </#meta>
                A<#include "included.ramet">B
                """;
        Files.writeString(tplDir.resolve("included.ramet"), included, StandardCharsets.UTF_8);
        Files.writeString(tplDir.resolve("host.ramet"), host, StandardCharsets.UTF_8);

        Ramet.run(tplDir, outDir, false);

        String content = Files.readString(outDir.resolve("host.java"), StandardCharsets.UTF_8);
        String flat = content.replace("\n", "");
        assertTrue(flat.contains("A[hello]B"), flat);
    }

    @Test
    void resolvesIncludesRelativeToIncludingFile(@TempDir Path tmpDir) throws IOException {
        // include 路径以「发起 include 的文件所在文件夹」为基准：
        // host 与 included 同处 sub/ 下，host 用相对路径 "included.ramet" 即可命中，
        // 不应误命中根目录下同名文件。
        Path tplDir = tmpDir.resolve("templates");
        Files.createDirectories(tplDir.resolve("sub"));
        Path outDir = tmpDir.resolve("out");

        String included = "<#meta>@Path{ \"inc.java\" }</#meta>[${x}]";
        String host = """
                <#meta>
                @Param{ x: "hi" }
                @Path{ "host.java" }
                </#meta>
                A<#include "included.ramet">B
                """;
        Files.writeString(tplDir.resolve("sub/included.ramet"), included, StandardCharsets.UTF_8);
        Files.writeString(tplDir.resolve("sub/host.ramet"), host, StandardCharsets.UTF_8);
        // 根目录下同名 include 源文件，用于确认 host 不会误命中它。
        // 注意：其输出路径刻意与 sub/included.ramet 不同，以避免跨模板输出路径碰撞。
        Files.writeString(tplDir.resolve("included.ramet"),
                "<#meta>@Path{ \"root-inc.java\" }</#meta>[ROOT]", StandardCharsets.UTF_8);

        Ramet.run(tplDir, outDir, false);

        String content = Files.readString(outDir.resolve("host.java"), StandardCharsets.UTF_8);
        String flat = content.replace("\n", "");
        assertTrue(flat.contains("A[hi]B"), flat);
        assertFalse(flat.contains("[ROOT]"), "不应误命中根目录下同名文件");
    }

    @Test
    void throwsOnMissingTemplateDir() {
        Path nonexistent = Path.of("/nonexistent/dir");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Ramet.run(nonexistent, Path.of("out"), false));
        assertTrue(ex.getMessage().contains("目录不存在"));
    }

    @Test
    void throwsOnCrossTemplateCaseInsensitivePathCollision(@TempDir Path tmpDir) throws IOException {
        // 两个模板输出仅在大小写不同的路径（dup.java vs DUP.java）。
        // 在大小写不敏感的文件系统上二者会解析为同一物理文件并相互覆盖，
        // 引擎应在写入前以小写统一键检测到碰撞并失败。
        Path tplDir = tmpDir.resolve("templates");
        Files.createDirectories(tplDir);
        Path outDir = tmpDir.resolve("out");

        String lower = "<#meta>@Path{ \"dup.java\" }</#meta>lower";
        String upper = "<#meta>@Path{ \"DUP.java\" }</#meta>UPPER";
        Files.writeString(tplDir.resolve("lower.ramet"), lower, StandardCharsets.UTF_8);
        Files.writeString(tplDir.resolve("upper.ramet"), upper, StandardCharsets.UTF_8);

        CodeGenException ex = assertThrows(CodeGenException.class,
                () -> Ramet.run(tplDir, outDir, false));
        assertTrue(ex.getMessage().contains("大小写不敏感碰撞"), ex.getMessage());
    }
}
