package com.flora.codegen;

import com.flora.codegen.engine.CodeGenException;
import com.flora.os.virtual.file.VFS;
import com.flora.os.virtual.file.backend.MemoryFileSystem;
import com.flora.os.virtual.file.nio.VfsFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖 {@link Ramet} 集成测试。
 * <p>所有文件操作在 VFS + MemoryFileSystem（通过 NIO 桥接）上执行，不涉及真实文件系统。</p>
 */
class RametTest {

    private static VfsFileSystem newMemFs() {
        VFS vfs = new VFS();
        vfs.mount("/", new MemoryFileSystem());
        return new VfsFileSystem(vfs);
    }

    @Test
    void generatesSingleTemplate() throws IOException {
        VfsFileSystem fs = newMemFs();
        Path tplDir = fs.getPath("/tpl");
        Files.createDirectories(tplDir);
        Files.writeString(fs.getPath("/tpl/test.ramet"), """
                <#meta>
                @Param{ pkg: "com.x", name: "Foo" }
                @Path{ "Foo.java" }
                </#meta>
                package ${pkg};
                public class ${name} {}
                """);

        Ramet.run(tplDir, fs.getPath("/out"), false);

        String content = Files.readString(fs.getPath("/out/Foo.java"));
        assertAll(
                () -> assertTrue(content.contains("package com.x;")),
                () -> assertTrue(content.contains("public class Foo {}"))
        );
    }

    @Test
    void dryRunDoesNotCreateOutput() throws IOException {
        VfsFileSystem fs = newMemFs();
        Path tplDir = fs.getPath("/tpl");
        Files.createDirectories(tplDir);
        Files.writeString(fs.getPath("/tpl/d.ramet"), "<#meta>@Path{ \"D.java\" }</#meta>body");

        Ramet.run(tplDir, fs.getPath("/out"), true);

        assertFalse(Files.exists(fs.getPath("/out")), "dry-run 模式下不应写入文件");
    }

    @Test
    void resolvesIncludes() throws IOException {
        VfsFileSystem fs = newMemFs();
        Path tplDir = fs.getPath("/tpl");
        Files.createDirectories(tplDir);
        Files.writeString(fs.getPath("/tpl/included.ramet"), "<#meta>@Path{ \"inc.java\" }</#meta>[${x}]");
        Files.writeString(fs.getPath("/tpl/host.ramet"), """
                <#meta>
                @Param{ x: "hello" }
                @Path{ "host.java" }
                </#meta>
                A<#include "included.ramet">B
                """);

        Ramet.run(tplDir, fs.getPath("/out"), false);

        String content = Files.readString(fs.getPath("/out/host.java")).replace("\n", "");
        assertTrue(content.contains("A[hello]B"), content);
    }

    @Test
    void resolvesIncludesRelativeToIncludingFile() throws IOException {
        VfsFileSystem fs = newMemFs();
        Path tplDir = fs.getPath("/tpl");
        Files.createDirectories(fs.getPath("/tpl/sub"));
        Files.writeString(fs.getPath("/tpl/sub/included.ramet"), "<#meta>@Path{ \"inc.java\" }</#meta>[${x}]");
        Files.writeString(fs.getPath("/tpl/sub/host.ramet"), """
                <#meta>
                @Param{ x: "hi" }
                @Path{ "host.java" }
                </#meta>
                A<#include "included.ramet">B
                """);
        Files.writeString(fs.getPath("/tpl/included.ramet"),
                "<#meta>@Path{ \"root-inc.java\" }</#meta>[ROOT]");

        Ramet.run(tplDir, fs.getPath("/out"), false);

        String content = Files.readString(fs.getPath("/out/host.java")).replace("\n", "");
        assertTrue(content.contains("A[hi]B"), content);
        assertFalse(content.contains("[ROOT]"), "不应误命中根目录下同名文件");
    }

    @Test
    void throwsOnMissingTemplateDir() {
        VfsFileSystem fs = newMemFs();
        // 不创建 /tpl 目录
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Ramet.run(fs.getPath("/tpl"), fs.getPath("/out"), false));
        assertTrue(ex.getMessage().contains("目录不存在"));
    }

    @Test
    void throwsOnCrossTemplateCaseInsensitivePathCollision() throws IOException {
        VfsFileSystem fs = newMemFs();
        Path tplDir = fs.getPath("/tpl");
        Files.createDirectories(tplDir);
        Files.writeString(fs.getPath("/tpl/lower.ramet"), "<#meta>@Path{ \"dup.java\" }</#meta>lower");
        Files.writeString(fs.getPath("/tpl/upper.ramet"), "<#meta>@Path{ \"DUP.java\" }</#meta>UPPER");

        CodeGenException ex = assertThrows(CodeGenException.class,
                () -> Ramet.run(tplDir, fs.getPath("/out"), false));
        assertTrue(ex.getMessage().contains("大小写不敏感碰撞"), ex.getMessage());
    }
}
