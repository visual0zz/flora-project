package com.flora.os.virtual.file.nio;

import com.flora.os.virtual.file.VFS;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;

/**
 * 基于 VFS 的 {@link FileSystem} 实现。
 * <p>路径统一为 UNIX 风格绝对路径，与 {@link VfsFileSystemProvider} 配合
 * 使 {@code Files.readString()} / {@code Files.walk()} 等 NIO API 可直接操作 VFS。</p>
 */
public final class VfsFileSystem extends FileSystem {

    private final VFS vfs;
    private final VfsFileSystemProvider provider;

    public VfsFileSystem(VFS vfs, VfsFileSystemProvider provider) {
        this.vfs = vfs;
        this.provider = provider;
    }

    /** 快速构造（使用新创建的 Provider）。 */
    public VfsFileSystem(VFS vfs) {
        this(vfs, new VfsFileSystemProvider(vfs));
    }

    public VFS vfs() { return vfs; }

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
        // 确保以 / 开头
        String raw = sb.toString();
        if (!raw.startsWith("/")) raw = "/" + raw;
        // 简化重复斜杠和 ..
        String normalized = normalizePath(raw);
        return new VfsPath(normalized, this);
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

    /** 简易路径归一化（消除 .. 和 .）。 */
    private static String normalizePath(String p) {
        String[] parts = p.split("/", -1);
        Deque<String> stack = new ArrayDeque<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (!stack.isEmpty()) stack.removeLast(); }
            else stack.addLast(part);
        }
        if (stack.isEmpty()) return "/";
        return "/" + String.join("/", stack);
    }
}
