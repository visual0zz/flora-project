package com.flora.runtime.virtual.filesys;

import com.flora.os.UnixPathUtil;
import com.flora.runtime.virtual.filesys.bridge.MountTable;
import com.flora.runtime.virtual.filesys.bridge.VfsFileSystemProvider;
import com.flora.runtime.virtual.filesys.bridge.VfsPath;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;

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

    private final MountTable mountTable;
    private final VfsFileSystemProvider provider;
    private volatile boolean closed;
    private volatile Runnable onClose;

    /** 创建空的 VFS 实例，后续通过 {@link #mount} 添加后端。 */
    public VfsFileSystem() {
        this.mountTable = new MountTable();
        this.provider = new VfsFileSystemProvider(this, mountTable);
    }

    /** 注册关闭回调（由创建者注入，用于清理 URI 等外部注册）。 */
    public void onClose(Runnable action) {
        this.onClose = action;
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
        for (FSBackend b : mountTable.backends()) {
            try {
                b.close();
            } catch (IOException e) {
                if (ex == null) ex = e;
                else ex.addSuppressed(e);
            }
        }
        Runnable action = onClose;
        if (action != null) action.run();
        provider.deregister(this);
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
    public Path getPath(@NotNull String first, String... more) {
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
        mountTable.mount(path, backend);
    }

    /** 卸载指定路径的后端。 */
    public void unmount(String path) {
        ensureOpen();
        mountTable.unmount(path);
    }
}
