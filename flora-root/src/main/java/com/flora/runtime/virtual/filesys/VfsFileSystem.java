package com.flora.runtime.virtual.filesys;

import com.flora.os.UnixPathUtil;
import com.flora.runtime.virtual.filesys.bridge.FSBackendMatch;
import com.flora.runtime.virtual.filesys.bridge.VfsFileSystemProvider;
import com.flora.runtime.virtual.filesys.bridge.VfsPath;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.stream.Collectors;

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

    /** 挂载表快照，通过 {@link #mount} / {@link #unmount} 原子替换。 */
    private volatile List<Mount> mounts = List.of();
    private final VfsFileSystemProvider provider;
    private volatile boolean closed;

    /** 创建空的 VFS 实例，后续通过 {@link #mount} 添加后端。 */
    public VfsFileSystem() {
        this.provider = new VfsFileSystemProvider(this);
    }

    @Override @NotNull
    public FileSystemProvider provider() { return provider; }

    @Override @NotNull
    public String getSeparator() { return "/"; }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public boolean isOpen() { return !closed; }

    /**
     * 关闭虚拟文件系统。
     * <p>关闭所有已挂载后端的资源，清空挂载表。
     * 关闭后再调用 {@link #mount} / {@link #unmount} / 文件操作将抛出
     * {@link ClosedFileSystemException}。</p>
     */
    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException ex = null;
        for (Mount m : mounts) {
            try {
                m.backend().close();
            } catch (IOException e) {
                if (ex == null) ex = e;
                else ex.addSuppressed(e);
            }
        }
        mounts = List.of();
        if (ex != null) throw ex;
    }

    private void ensureOpen() {
        if (closed) throw new ClosedFileSystemException();
    }

    @Override @NotNull
    public Set<String> supportedFileAttributeViews() {
        return Set.of("basic");
    }

    @Override @NotNull
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

    @Override @NotNull
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

    /** 挂载一个后端到指定路径。路径自动归一化。挂载表以最长前缀优先匹配。 */
    public void mount(String path, FSBackend backend) {
        ensureOpen();
        String normalized = UnixPathUtil.normalize(path);
        List<Mount> updated = new ArrayList<>(mounts);
        updated.add(new Mount(normalized, backend));
        updated.sort(Comparator.comparingInt((Mount m) -> m.prefix.length()).reversed());
        mounts = List.copyOf(updated); // 原子发布：读者永远看到一致的全量快照
    }

    /** 卸载指定路径的后端。 */
    public void unmount(String path) {
        ensureOpen();
        String normalized = UnixPathUtil.normalize(path);
        List<Mount> updated = new ArrayList<>(mounts);
        updated.removeIf(m -> m.prefix.equals(normalized));
        mounts = List.copyOf(updated); // 原子发布
    }

    // ===================== 路径解析 =====================

    /**
     * 解析虚拟路径到后端。
     * <p>返回当前挂载表快照中匹配最长前缀的 {@link FSBackendMatch}。
     * 该操作是线程安全的：挂载表变更产生新的不可变快照，当前快照不受影响。</p>
     */
    public FSBackendMatch resolveInternal(String path) {
        ensureOpen();
        List<Mount> snapshot = mounts; // volatile 读，获取当前快照
        if (path.equals("/")) return new FSBackendMatch("/", new RootBackend());
        for (Mount m : snapshot) {
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

    /** 获取当前挂载表快照（供 RootBackend.list 使用）。 */
    List<Mount> mountSnapshot() { return mounts; }

    // ===================== 内部 =====================

    record Mount(String prefix, FSBackend backend) {}

    // ===================== 虚拟根目录后端 =====================

    /** 虚拟根目录后端 —— 仅返回挂载点列表。 */
    private class RootBackend implements FSBackend {
        @Override public FileAttributes getAttributes(String path, boolean followLinks) {
            if (path.equals("/")) return new FileAttributes(true, false, true, false, 0, 0, 0, true, false);
            return FileAttributes.NOT_FOUND;
        }
        @Override public java.nio.channels.SeekableByteChannel openChannel(String path, java.util.Set<? extends java.nio.file.OpenOption> options) throws java.io.IOException {
            throw new java.nio.file.FileSystemException("根目录不支持文件读写");
        }
        @Override public FileOpResult createDirectory(String path) { return FileOpResult.ALREADY_EXISTS; }
        @Override public FileOpResult delete(String path) { return FileOpResult.NOT_FOUND; }
        @Override public FileOpResult rename(String src, String dest) { return FileOpResult.NOT_FOUND; }
        @Override public java.util.List<String> list(String path) {
            return mountSnapshot().stream().map(Mount::prefix).sorted().toList();
        }
    }
}
