package com.flora.os.virtual;

import com.flora.runtime.virtual.filesys.backend.MemoryFileSystem;
import com.flora.runtime.virtual.filesys.backend.RealFileSystem;
import com.flora.runtime.virtual.filesys.VfsFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 虚拟文件系统测试。
 */
class VFSTest {

    // ===================== 基础操作 =====================

    @Test
    void mountAndWriteRead() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Path p = fs.getPath("/mem/hello.txt");
        assertFalse(Files.exists(p));

        Files.writeString(p, "Hello VFS!");
        assertTrue(Files.exists(p));
        assertTrue(Files.isRegularFile(p));
        assertEquals("Hello VFS!", Files.readString(p));
    }

    @Test
    void appendContent() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Path p = fs.getPath("/mem/log.txt");
        Files.writeString(p, "line1\n");
        Files.writeString(p, "line2\n", java.nio.file.StandardOpenOption.APPEND);
        assertEquals("line1\nline2\n", Files.readString(p));
    }

    // ===================== 目录操作 =====================

    @Test
    void createDirectories() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Path dir = fs.getPath("/mem/a/b/c");
        assertFalse(Files.exists(dir));

        Files.createDirectories(dir);
        assertTrue(Files.exists(dir));
        assertTrue(Files.isDirectory(dir));
    }

    @Test
    void listDirectory() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Files.writeString(fs.getPath("/mem/a.txt"), "a");
        Files.createDirectories(fs.getPath("/mem/sub"));
        Files.writeString(fs.getPath("/mem/sub/b.txt"), "b");

        try (var stream = Files.list(fs.getPath("/mem"))) {
            List<Path> rootFiles = stream.toList();
            assertEquals(2, rootFiles.size());
        }

        try (var stream = Files.list(fs.getPath("/mem/sub"))) {
            List<Path> subFiles = stream.toList();
            assertEquals(1, subFiles.size());
        }
    }

    // ===================== 删除与重命名 =====================

    @Test
    void deleteFile() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Path p = fs.getPath("/mem/tmp.txt");
        Files.writeString(p, "temp");
        assertTrue(Files.exists(p));

        Files.delete(p);
        assertFalse(Files.exists(p));
    }

    @Test
    void renameFile() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Path src = fs.getPath("/mem/old.txt");
        Files.writeString(src, "data");
        Path dest = fs.getPath("/mem/new.txt");

        Files.move(src, dest);
        assertFalse(Files.exists(src));
        assertTrue(Files.exists(dest));
        assertEquals("data", Files.readString(dest));
    }

    // ===================== 拷贝 =====================

    @Test
    void copyFile() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Path src = fs.getPath("/mem/src.txt");
        Files.writeString(src, "source data");
        Path dest = fs.getPath("/mem/dst.txt");

        Files.copy(src, dest);
        assertEquals("source data", Files.readString(dest));
    }

    @Test
    void copyReplaceExisting() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Files.writeString(fs.getPath("/mem/src.txt"), "src");
        Files.writeString(fs.getPath("/mem/dst.txt"), "dst");

        Files.copy(fs.getPath("/mem/src.txt"), fs.getPath("/mem/dst.txt"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertEquals("src", Files.readString(fs.getPath("/mem/dst.txt")));
    }

    @Test
    void copyExistingNoReplaceThrows() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Files.writeString(fs.getPath("/mem/src.txt"), "src");
        Files.writeString(fs.getPath("/mem/dst.txt"), "dst");

        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> Files.copy(fs.getPath("/mem/src.txt"), fs.getPath("/mem/dst.txt")));
    }

    // ===================== 路径导航 =====================

    @Test
    void pathNavigation() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Files.createDirectories(fs.getPath("/mem/a/b"));
        Files.writeString(fs.getPath("/mem/a/b/c.txt"), "deep");

        Path f = fs.getPath("/mem/a/b/c.txt");
        assertEquals("c.txt", f.getFileName().toString());
        assertEquals("/mem/a/b/c.txt", f.toString());
        assertEquals("/mem/a/b", f.getParent().toString());
        assertEquals("/mem/a/b/d.txt", f.getParent().resolve("d.txt").toString());
        assertEquals("/mem/a/b/c.txt/sub", f.resolve("sub").toString());
    }

    // ===================== 异常路径 =====================

    @Test
    void readNonExistentThrows() {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        assertThrows(java.nio.file.NoSuchFileException.class,
                () -> Files.readString(fs.getPath("/mem/nope.txt")));
    }

    @Test
    void nonExistentAttributes() {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        var attr = fs.getPath("/mem/nope.txt");
        assertFalse(Files.exists(attr));
    }

    @Test
    void noMountThrows() {
        VfsFileSystem fs = new VfsFileSystem();
        // 未挂载任何后端，通过 Provider 访问会触发 resolveInternal 的 ISE
        Path p = fs.getPath("/unknown/path");
        assertThrows(IllegalStateException.class,
                () -> java.nio.file.Files.readAttributes(p, BasicFileAttributes.class));
    }

    // ===================== 目录树特性 =====================

    @Test
    void nestedFileAccess() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Files.createDirectories(fs.getPath("/mem/data/sub/deep"));
        Files.writeString(fs.getPath("/mem/data/sub/deep/file.txt"), "nested");

        assertTrue(Files.isDirectory(fs.getPath("/mem/data")));
        assertTrue(Files.isRegularFile(fs.getPath("/mem/data/sub/deep/file.txt")));
        assertEquals("nested", Files.readString(fs.getPath("/mem/data/sub/deep/file.txt")));
    }

    @Test
    void emptyDirectory() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Files.createDirectory(fs.getPath("/mem/empty"));
        try (var stream = Files.list(fs.getPath("/mem/empty"))) {
            assertTrue(stream.findAny().isEmpty());
        }
    }

    // ===================== 多个挂载点 =====================

    @Test
    void multipleMounts() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/sys", new MemoryFileSystem());
        fs.mount("/data", new MemoryFileSystem());

        Files.writeString(fs.getPath("/sys/config.yml"), "sys: true");
        Files.writeString(fs.getPath("/data/records.db"), "records");

        assertEquals("sys: true", Files.readString(fs.getPath("/sys/config.yml")));
        assertEquals("records", Files.readString(fs.getPath("/data/records.db")));
    }

    @Test
    void unmountRemovesAccess() {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/tmp", new MemoryFileSystem());
        fs.unmount("/tmp");
        Path p = fs.getPath("/tmp/x");
        // 卸载后通过 Provider 访问应触发 ISE
        assertThrows(IllegalStateException.class,
                () -> java.nio.file.Files.readAttributes(p, BasicFileAttributes.class));
    }

    // ===================== RealFileSystem =====================

    @Test
    void realFileSystemRoundTrip() throws IOException {
        java.nio.file.Path tmpDir = Files.createTempDirectory("vfs-test-");
        try {
            VfsFileSystem fs = new VfsFileSystem();
            fs.mount("/real", new RealFileSystem(tmpDir));
            Path p = fs.getPath("/real/test.txt");
            Files.writeString(p, "real fs test");
            assertTrue(Files.exists(p));
            assertEquals("real fs test", Files.readString(p));
        } finally {
            try (var s = Files.walk(tmpDir)) {
                s.sorted(java.util.Comparator.reverseOrder())
                 .map(java.nio.file.Path::toFile)
                 .forEach(java.io.File::delete);
            }
        }
    }

    // ===================== createFile =====================

    @Test
    void createFileThenExists() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/mem", new MemoryFileSystem());
        Path p = fs.getPath("/mem/newfile.dat");
        Files.writeString(p, "data");
        assertTrue(Files.exists(p));
        // 写入已有文件应成功（覆盖模式）
        Files.writeString(p, "overwritten");
        assertEquals("overwritten", Files.readString(p));
    }

    // ===================== 隔离实例 =====================

    @Test
    void isolatedInstanceDoesNotShareMounts() {
        VfsFileSystem fs1 = new VfsFileSystem();
        VfsFileSystem fs2 = new VfsFileSystem();

        fs1.mount("/data", new MemoryFileSystem());

        // fs1 可以看到挂载
        assertDoesNotThrow(() ->
                java.nio.file.Files.readAttributes(fs1.getPath("/data"), BasicFileAttributes.class));
        // fs2 看不到
        Path p2 = fs2.getPath("/data");
        assertThrows(IllegalStateException.class,
                () -> java.nio.file.Files.readAttributes(p2, BasicFileAttributes.class));
    }

    @Test
    void isolatedInstanceOperations() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/tmp", new MemoryFileSystem());

        Path p = fs.getPath("/tmp/test.txt");
        Files.writeString(p, "isolated");
        assertEquals("isolated", Files.readString(p));
    }

    // ===================== 符号链接 =====================

    @Test
    void symlinkCreateAndRead() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/", new MemoryFileSystem());

        Files.writeString(fs.getPath("/target.txt"), "hello");
        assertTrue(Files.exists(fs.getPath("/target.txt")));

        Files.createSymbolicLink(fs.getPath("/link.txt"), fs.getPath("/target.txt"));

        assertTrue(Files.isSymbolicLink(fs.getPath("/link.txt")));
        assertEquals("/target.txt", Files.readSymbolicLink(fs.getPath("/link.txt")).toString());
        assertEquals("hello", Files.readString(fs.getPath("/link.txt")));
    }

    @Test
    void symlinkNoFollowAttributes() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/", new MemoryFileSystem());

        Files.writeString(fs.getPath("/real"), "data");
        Files.createSymbolicLink(fs.getPath("/link"), fs.getPath("/real"));

        BasicFileAttributes linkAttr = Files.readAttributes(fs.getPath("/link"),
                BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        assertTrue(linkAttr.isSymbolicLink());
        assertFalse(linkAttr.isRegularFile());

        BasicFileAttributes followAttr = Files.readAttributes(fs.getPath("/link"),
                BasicFileAttributes.class);
        assertTrue(followAttr.isRegularFile());
    }

    @Test
    void symlinkDeleteDoesNotRemoveTarget() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/", new MemoryFileSystem());

        Files.writeString(fs.getPath("/target"), "data");
        Files.createSymbolicLink(fs.getPath("/link"), fs.getPath("/target"));

        Files.delete(fs.getPath("/link"));
        assertTrue(Files.exists(fs.getPath("/target")));
        assertFalse(Files.exists(fs.getPath("/link")));
    }

    @Test
    void symlinkRelativeTarget() throws IOException {
        VfsFileSystem fs = new VfsFileSystem();
        fs.mount("/", new MemoryFileSystem());

        Files.createDirectories(fs.getPath("/dir"));
        Files.writeString(fs.getPath("/dir/target.txt"), "data");
        Files.createSymbolicLink(fs.getPath("/dir/link.txt"), fs.getPath("/dir/target.txt"));

        assertEquals("/dir/target.txt", Files.readSymbolicLink(fs.getPath("/dir/link.txt")).toString());
        assertEquals("data", Files.readString(fs.getPath("/dir/link.txt")));
    }
}
