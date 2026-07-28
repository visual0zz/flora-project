package com.flora.os.virtual;

import com.flora.os.virtual.file.VFile;
import com.flora.os.virtual.file.VFS;
import com.flora.os.virtual.file.backend.MemoryFileSystem;
import com.flora.os.virtual.file.backend.RealFileSystem;
import com.flora.os.virtual.file.nio.VfsFileSystem;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void setUp() {
        VFS.system().unmount("/mem");
    }

    // ===================== 基础操作 =====================

    @Test
    void mountAndWriteRead() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFile f = VFS.system().get("/mem/hello.txt");
        assertFalse(f.exists());

        f.writeString("Hello VFS!");
        assertTrue(f.exists());
        assertTrue(f.isRegularFile());
        assertEquals("Hello VFS!", f.readString());
    }

    @Test
    void appendContent() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFile f = VFS.system().get("/mem/log.txt");
        f.writeString("line1\n");
        try (var out = f.openOutputStream(true)) {
            out.write("line2\n".getBytes());
        }
        assertEquals("line1\nline2\n", f.readString());
    }

    // ===================== 目录操作 =====================

    @Test
    void createDirectories() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFile dir = VFS.system().get("/mem/a/b/c");
        assertFalse(dir.exists());

        assertTrue(dir.mkDirs());
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
    }

    @Test
    void listDirectory() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFS.system().get("/mem/a.txt").writeString("a");
        VFS.system().get("/mem/sub/b.txt").mkDirs();
        VFS.system().get("/mem/sub/b.txt").writeString("b");

        List<VFile> rootFiles = VFS.system().get("/mem").list();
        assertEquals(2, rootFiles.size());

        List<VFile> subFiles = VFS.system().get("/mem/sub").list();
        assertEquals(1, subFiles.size());
    }

    // ===================== 删除与重命名 =====================

    @Test
    void deleteFile() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFS.system().get("/mem/tmp.txt").writeString("temp");
        assertTrue(VFS.system().get("/mem/tmp.txt").exists());

        assertTrue(VFS.system().get("/mem/tmp.txt").delete());
        assertFalse(VFS.system().get("/mem/tmp.txt").exists());
    }

    @Test
    void renameFile() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFS.system().get("/mem/old.txt").writeString("data");
        VFile dest = VFS.system().get("/mem/new.txt");

        assertTrue(VFS.system().get("/mem/old.txt").renameTo(dest));
        assertFalse(VFS.system().get("/mem/old.txt").exists());
        assertTrue(dest.exists());
        assertEquals("data", dest.readString());
    }

    // ===================== 拷贝 =====================

    @Test
    void copyFile() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFS.system().get("/mem/src.txt").writeString("source data");
        VFile dest = VFS.system().get("/mem/dst.txt");

        VFS.system().get("/mem/src.txt").copyTo(dest, false);
        assertEquals("source data", dest.readString());
    }

    @Test
    void copyReplaceExisting() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFS.system().get("/mem/src.txt").writeString("src");
        VFS.system().get("/mem/dst.txt").writeString("dst");

        VFS.system().get("/mem/src.txt").copyTo(VFS.system().get("/mem/dst.txt"), true);
        assertEquals("src", VFS.system().get("/mem/dst.txt").readString());
    }

    @Test
    void copyExistingNoReplaceThrows() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFS.system().get("/mem/src.txt").writeString("src");
        VFS.system().get("/mem/dst.txt").writeString("dst");

        assertThrows(IOException.class,
                () -> VFS.system().get("/mem/src.txt").copyTo(VFS.system().get("/mem/dst.txt"), false));
    }

    // ===================== 导航 =====================

    @Test
    void pathNavigation() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFS.system().get("/mem/a/b/c.txt").mkDirs();
        VFS.system().get("/mem/a/b/c.txt").writeString("deep");

        VFile f = VFS.system().get("/mem/a/b/c.txt");
        assertEquals("c.txt", f.getName());
        assertEquals("/mem/a/b/c.txt", f.getPath());
        assertEquals("/mem/a/b", f.getParent().getPath());
        assertEquals("/mem/a/b/d.txt", f.resolveSibling("d.txt").getPath());
        assertEquals("/mem/a/b/c.txt/sub", f.resolve("sub").getPath());
    }

    // ===================== 异常路径 =====================

    @Test
    void readNonExistentThrows() {
        VFS.system().mount("/mem", new MemoryFileSystem());
        assertThrows(IOException.class, () -> VFS.system().get("/mem/nope.txt").readString());
    }

    @Test
    void nonExistentAttributes() {
        VFS.system().mount("/mem", new MemoryFileSystem());
        var attr = VFS.system().get("/mem/nope.txt").getAttributes();
        assertFalse(attr.exists());
    }

    @Test
    void noMountThrows() {
        assertThrows(IllegalStateException.class, () -> VFS.system().get("/unknown/path"));
    }

    // ===================== 目录树特性 =====================

    @Test
    void nestedFileAccess() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFS.system().get("/mem/data/sub/deep/file.txt").mkDirs();
        VFS.system().get("/mem/data/sub/deep/file.txt").writeString("nested");

        assertTrue(VFS.system().get("/mem/data").isDirectory());
        assertTrue(VFS.system().get("/mem/data/sub/deep/file.txt").isRegularFile());
        assertEquals("nested", VFS.system().get("/mem/data/sub/deep/file.txt").readString());
    }

    @Test
    void emptyDirectory() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFS.system().get("/mem/empty").mkDir();
        assertTrue(VFS.system().get("/mem/empty").list().isEmpty());
    }

    // ===================== 多个挂载点 =====================

    @Test
    void multipleMounts() throws IOException {
        VFS.system().mount("/sys", new MemoryFileSystem());
        VFS.system().mount("/data", new MemoryFileSystem());

        VFS.system().get("/sys/config.yml").writeString("sys: true");
        VFS.system().get("/data/records.db").writeString("records");

        assertEquals("sys: true", VFS.system().get("/sys/config.yml").readString());
        assertEquals("records", VFS.system().get("/data/records.db").readString());
    }

    @Test
    void unmountRemovesAccess() {
        VFS.system().mount("/tmp", new MemoryFileSystem());
        VFS.system().unmount("/tmp");
        assertThrows(IllegalStateException.class, () -> VFS.system().get("/tmp/x"));
    }

    // ===================== RealFileSystem =====================

    @Test
    void realFileSystemRoundTrip() throws IOException {
        Path tmpDir = Files.createTempDirectory("vfs-test-");
        try {
            VFS.system().mount("/real", new RealFileSystem(tmpDir));
            VFS.system().get("/real/test.txt").writeString("real fs test");
            assertTrue(VFS.system().get("/real/test.txt").exists());
            assertEquals("real fs test", VFS.system().get("/real/test.txt").readString());
        } finally {
            // 清理
            try (var s = Files.walk(tmpDir)) {
                s.sorted(java.util.Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(java.io.File::delete);
            }
        }
    }

    // ===================== createFile =====================

    @Test
    void createFileThenExists() throws IOException {
        VFS.system().mount("/mem", new MemoryFileSystem());
        VFile f = VFS.system().get("/mem/newfile.dat");
        assertTrue(f.createFile());
        assertTrue(f.exists());
        assertFalse(f.createFile()); // already exists
    }

    // ===================== 隔离实例 =====================

    @Test
    void isolatedInstanceDoesNotShareMounts() {
        VFS vfs1 = new VFS();
        VFS vfs2 = new VFS();

        vfs1.mount("/data", new MemoryFileSystem());

        // vfs1 可以看到挂载
        assertDoesNotThrow(() -> vfs1.get("/data"));
        // vfs2 看不到
        assertThrows(IllegalStateException.class, () -> vfs2.get("/data"));
        // system() 也看不到
        assertThrows(IllegalStateException.class, () -> VFS.system().get("/data"));
    }

    @Test
    void isolatedInstanceOperations() throws IOException {
        VFS vfs = new VFS();
        vfs.mount("/tmp", new MemoryFileSystem());

        VFile f = vfs.get("/tmp/test.txt");
        f.writeString("isolated");
        assertEquals("isolated", f.readString());
    }

    // ===================== 符号链接 =====================

    @Test
    void symlinkCreateAndRead() throws IOException {
        VFS vfs = new VFS();
        vfs.mount("/", new MemoryFileSystem());

        vfs.get("/target.txt").writeString("hello");

        assertTrue(vfs.get("/target.txt").exists());

        // 通过 NIO 创建符号链接
        VfsFileSystem fs = new VfsFileSystem(vfs);
        Files.createSymbolicLink(fs.getPath("/link.txt"), fs.getPath("/target.txt"));

        // 读取链接目标
        assertTrue(Files.isSymbolicLink(fs.getPath("/link.txt")));
        assertEquals("/target.txt", Files.readSymbolicLink(fs.getPath("/link.txt")).toString());

        // 通过符号链接读取内容
        assertEquals("hello", Files.readString(fs.getPath("/link.txt")));
    }

    @Test
    void symlinkNoFollowAttributes() throws IOException {
        VFS vfs = new VFS();
        vfs.mount("/", new MemoryFileSystem());
        VfsFileSystem fs = new VfsFileSystem(vfs);

        vfs.get("/real").writeString("data");
        Files.createSymbolicLink(fs.getPath("/link"), fs.getPath("/real"));

        // 不跟随链接的读属性 → 标记为符号链接
        BasicFileAttributes linkAttr = Files.readAttributes(fs.getPath("/link"),
                BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        assertTrue(linkAttr.isSymbolicLink());
        assertFalse(linkAttr.isRegularFile());

        // 跟随链接的读属性 → 标记为普通文件
        BasicFileAttributes followAttr = Files.readAttributes(fs.getPath("/link"),
                BasicFileAttributes.class);
        assertTrue(followAttr.isRegularFile());
    }

    @Test
    void symlinkDeleteDoesNotRemoveTarget() throws IOException {
        VFS vfs = new VFS();
        vfs.mount("/", new MemoryFileSystem());
        VfsFileSystem fs = new VfsFileSystem(vfs);

        vfs.get("/target").writeString("data");
        Files.createSymbolicLink(fs.getPath("/link"), fs.getPath("/target"));

        Files.delete(fs.getPath("/link"));
        assertTrue(vfs.get("/target").exists());
        assertFalse(vfs.get("/link").exists());
    }

    @Test
    void symlinkRelativeTarget() throws IOException {
        VFS vfs = new VFS();
        vfs.mount("/", new MemoryFileSystem());
        VfsFileSystem fs = new VfsFileSystem(vfs);

        vfs.get("/dir/target.txt").mkDirs();
        vfs.get("/dir/target.txt").writeString("data");
        // VfsFileSystem 路径总是绝对路径，因此符号链接目标为 /dir/target.txt
        Files.createSymbolicLink(fs.getPath("/dir/link.txt"), fs.getPath("dir/target.txt"));

        assertEquals("/dir/target.txt", Files.readSymbolicLink(fs.getPath("/dir/link.txt")).toString());
        assertEquals("data", Files.readString(fs.getPath("/dir/link.txt")));
    }
}
