package com.flora.codegen;

import com.flora.codegen.engine.CodeGenException;
import com.flora.os.virtual.file.VFS;
import com.flora.os.virtual.file.backend.MemoryFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖 {@link Ramet} 集成测试。
 * <p>所有文件操作在 VFS + MemoryFileSystem 上执行，不涉及真实文件系统。</p>
 */
class RametTest {

    private static VFS createMemVfs() {
        VFS vfs = new VFS();
        vfs.mount("/", new MemoryFileSystem());
        return vfs;
    }

    @Test
    void generatesSingleTemplate() throws IOException {
        VFS vfs = createMemVfs();
        vfs.get("/tpl", "test.ramet").writeString("""
                <#meta>
                @Param{ pkg: "com.x", name: "Foo" }
                @Path{ "Foo.java" }
                </#meta>
                package ${pkg};
                public class ${name} {}
                """);

        Ramet.run(vfs, "/tpl", "/out", false);

        String content = vfs.get("/out/Foo.java").readString();
        assertAll(
                () -> assertTrue(content.contains("package com.x;")),
                () -> assertTrue(content.contains("public class Foo {}"))
        );
    }

    @Test
    void dryRunDoesNotCreateOutput() throws IOException {
        VFS vfs = createMemVfs();
        vfs.get("/tpl", "d.ramet").writeString("<#meta>@Path{ \"D.java\" }</#meta>body");

        Ramet.run(vfs, "/tpl", "/out", true);

        assertFalse(vfs.get("/out").exists(), "dry-run 模式下不应写入文件");
    }

    @Test
    void resolvesIncludes() throws IOException {
        VFS vfs = createMemVfs();
        vfs.get("/tpl", "included.ramet").writeString("<#meta>@Path{ \"inc.java\" }</#meta>[${x}]");
        vfs.get("/tpl", "host.ramet").writeString("""
                <#meta>
                @Param{ x: "hello" }
                @Path{ "host.java" }
                </#meta>
                A<#include "included.ramet">B
                """);

        Ramet.run(vfs, "/tpl", "/out", false);

        String content = vfs.get("/out/host.java").readString().replace("\n", "");
        assertTrue(content.contains("A[hello]B"), content);
    }

    @Test
    void resolvesIncludesRelativeToIncludingFile() throws IOException {
        // include 路径以「发起 include 的文件所在文件夹」为基准
        VFS vfs = createMemVfs();
        vfs.get("/tpl/sub", "included.ramet").mkDirs();
        vfs.get("/tpl/sub/included.ramet").writeString("<#meta>@Path{ \"inc.java\" }</#meta>[${x}]");
        vfs.get("/tpl/sub/host.ramet").writeString("""
                <#meta>
                @Param{ x: "hi" }
                @Path{ "host.java" }
                </#meta>
                A<#include "included.ramet">B
                """);
        // 根目录下同名文件，不应误命中
        vfs.get("/tpl/included.ramet").writeString("<#meta>@Path{ \"root-inc.java\" }</#meta>[ROOT]");

        Ramet.run(vfs, "/tpl", "/out", false);

        String content = vfs.get("/out/host.java").readString().replace("\n", "");
        assertTrue(content.contains("A[hi]B"), content);
        assertFalse(content.contains("[ROOT]"), "不应误命中根目录下同名文件");
    }

    @Test
    void throwsOnMissingTemplateDir() {
        VFS vfs = new VFS();
        vfs.mount("/", new MemoryFileSystem());
        // 不创建 /tpl 目录
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Ramet.run(vfs, "/tpl", "/out", false));
        assertTrue(ex.getMessage().contains("目录不存在"));
    }

    @Test
    void throwsOnCrossTemplateCaseInsensitivePathCollision() throws IOException {
        VFS vfs = createMemVfs();
        vfs.get("/tpl", "lower.ramet").writeString("<#meta>@Path{ \"dup.java\" }</#meta>lower");
        vfs.get("/tpl", "upper.ramet").writeString("<#meta>@Path{ \"DUP.java\" }</#meta>UPPER");

        CodeGenException ex = assertThrows(CodeGenException.class,
                () -> Ramet.run(vfs, "/tpl", "/out", false));
        assertTrue(ex.getMessage().contains("大小写不敏感碰撞"), ex.getMessage());
    }
}
