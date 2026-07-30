package com.flora.runtime.virtual.filesys.nio;

import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

/**
 * VFS 目录流，用于 {@code Files.walk()} / {@code Files.newDirectoryStream()}。
 */
final class VfsDirectoryStream implements DirectoryStream<Path> {

    private final List<Path> entries;
    private boolean closed;

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
