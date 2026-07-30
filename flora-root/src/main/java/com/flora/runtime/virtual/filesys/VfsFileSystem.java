package com.flora.runtime.virtual.filesys;

import com.flora.os.UnixPathUtil;
import com.flora.runtime.virtual.filesys.nio.FSBackendMatch;
import com.flora.runtime.virtual.filesys.nio.VfsFileSystemProvider;
import com.flora.runtime.virtual.filesys.nio.VfsPath;

import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 虚拟文件系统的 {@link FileSystem} 实现。
 * <p>同时管理挂载表：将虚拟路径路由到对应的 {@link FSBackend}。
 * 路径统一为 UNIX 风格绝对路径，与 {@link VfsFileSystemProvider} 配合
 * 使 {@code Files.readString()} / {@code Files.walk()} 等 NIO API 可直接操作 VFS。</p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * VfsFileSystem fs = new VfsFileSystem();
 * fs.mount("/data", new MemoryFileSystem());
 * Path p = fs.getPath("/data/hello.txt");
 * Files.writeString(p, "Hello!");
 * String text = Files.readString(p);
 * }</pre>
 */
public final class VfsFileSystem extends FileSystem {

    private final List<Mount> mounts = new CopyOnWriteArrayList<>();
    private final VfsFileSystemProvider provider;

    /** 创建空的 VFS 实例，后续通过 {@link #mount} 添加后端。 */
    public VfsFileSystem() {
        this.provider = new VfsFileSystemProvider(this);
    }

    @Override
    public FileSystemProvider provider() { return provider; }

    @Override
    public String getSeparator() { return "/"; }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public boolean isOpen() { return true; }

    @Override
    public void close() { /* VFS 实例由外部管理 */ }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return Set.of("basic");
    }

    @Override
    public Path getPath(String first, String... more) {
        StringBuilder sb = new StringBuilder(first);
        for (String m : more) sb.append('/').append(m);
        String raw = sb.toString();
        if (!raw.startsWith("/")) raw = "/" + raw;
        return new VfsPath(UnixPathUtil.normalize(raw), this);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return List.of(getPath("/"));
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return List.of();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    // ===================== 挂载管理 =====================

    /** 挂载一个后端到指定路径。路径自动归一化。 */
    public void mount(String path, FSBackend backend) {
        String normalized = UnixPathUtil.normalize(path);
        mounts.add(new Mount(normalized, backend));
        mounts.sort(Comparator.comparingInt((Mount m) -> m.prefix.length()).reversed());
    }

    /** 卸载指定路径的后端。 */
    public void unmount(String path) {
        String normalized = UnixPathUtil.normalize(path);
        mounts.removeIf(m -> m.prefix.equals(normalized));
    }

    // ===================== 路径解析 =====================

    public FSBackendMatch resolveInternal(String path) {
        if (path.equals("/")) return new FSBackendMatch("/", new RootBackend());
        for (Mount m : mounts) {
            if (m.prefix.equals("/") || path.equals(m.prefix) || path.startsWith(m.prefix + "/")) {
                String relative;
                if (m.prefix.equals("/")) relative = path;
                else if (path.equals(m.prefix)) relative = "/";
                else relative = path.substring(m.prefix.length());
                return new FSBackendMatch(relative, m.backend);
            }
        }
        throw new IllegalStateException("未找到匹配的挂载点: " + path);
    }

    // ===================== 内部 =====================

    private record Mount(String prefix, FSBackend backend) {}

    // ===================== 虚拟根目录后端 =====================

    /** 虚拟根目录后端 —— 仅返回挂载点列表。 */
    private class RootBackend implements FSBackend {
        @Override public FileAttributes getAttributes(String path, boolean followLinks) {
            if (path.equals("/")) return new FileAttributes(true, false, true, false, 0, 0, 0, true, false);
            return FileAttributes.NOT_FOUND;
        }
        @Override public java.io.InputStream read(String path) { throw new UnsupportedOperationException("根目录不支持读"); }
        @Override public java.io.OutputStream write(String path, boolean append) { throw new UnsupportedOperationException("根目录不支持写"); }
        @Override public boolean createDirectory(String path) { return false; }
        @Override public boolean delete(String path) { return false; }
        @Override public boolean rename(String src, String dest) { return false; }
        @Override public java.util.List<String> list(String path) {
            return mounts.stream().map(Mount::prefix).sorted().toList();
        }
    }
}
