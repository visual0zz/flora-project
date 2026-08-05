package com.flora.runtime.virtual.filesys.bridge;

import com.flora.tag.ThreadFragile;

import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

/**
 * VFS 目录流，用于 {@code Files.walk()} / {@code Files.newDirectoryStream()}。
 * <p>内部状态只有 {@code closed} 标记，但并发 {@link #close()} + {@link #iterator()} 调用
 * 可能导致 {@code closed} 可见性问题。</p>
 */
@ThreadFragile("closed 非 volatile，并发 close()+iterator() 可能读到陈旧 closed")
final class VfsDirectoryStream implements DirectoryStream<Path> {

    private final List<Path> entries;
    private volatile boolean closed;

    VfsDirectoryStream(List<Path> entries) {
        this.entries = entries;
    }

    @Override
    public Iterator<Path> iterator() {
        if (closed) throw new IllegalStateException("DirectoryStream 已关闭");
        return entries.iterator();
    }

    @Override
    public void close() {
        closed = true;
    }
}
