package com.flora.runtime.virtual.filesys.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;

/**
 * 简易 {@link FileChannel} 实现，委托给 {@link SeekableByteChannel}。
 * <p>支持基本读写和位置查询，不支持映射/锁定/传输直接到流。</p>
 */
final class VfsFileChannel extends FileChannel {

    private final SeekableByteChannel delegate;

    VfsFileChannel(SeekableByteChannel delegate) {
        this.delegate = delegate;
    }

    @Override public int read(ByteBuffer dst) throws IOException { return delegate.read(dst); }
    @Override public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long total = 0;
        for (int i = offset; i < offset + length; i++) { int r = delegate.read(dsts[i]); if (r < 0) break; total += r; }
        return total;
    }
    @Override public int write(ByteBuffer src) throws IOException { return delegate.write(src); }
    @Override public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        long total = 0;
        for (int i = offset; i < offset + length; i++) total += delegate.write(srcs[i]);
        return total;
    }
    @Override public long position() throws IOException { return delegate.position(); }
    @Override public FileChannel position(long newPosition) throws IOException { delegate.position(newPosition); return this; }
    @Override public long size() throws IOException { return delegate.size(); }
    @Override public FileChannel truncate(long size) throws IOException { delegate.truncate(size); return this; }
    @Override public void force(boolean metaData) { }
    @Override public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        delegate.position(position);
        long total = 0;
        ByteBuffer buf = ByteBuffer.allocate(8192);
        while (total < count) {
            buf.clear();
            buf.limit((int) Math.min(8192, count - total));
            int r = delegate.read(buf);
            if (r < 0) break;
            buf.flip();
            target.write(buf);
            total += r;
        }
        return total;
    }
    @Override public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        delegate.position(position);
        long total = 0;
        ByteBuffer buf = ByteBuffer.allocate(8192);
        while (total < count) {
            buf.clear();
            buf.limit((int) Math.min(8192, count - total));
            int r = src.read(buf);
            if (r < 0) break;
            buf.flip();
            delegate.write(buf);
            total += r;
        }
        return total;
    }
    @Override public int read(ByteBuffer dst, long position) throws IOException {
        delegate.position(position);
        return delegate.read(dst);
    }
    @Override public int write(ByteBuffer src, long position) throws IOException {
        delegate.position(position);
        return delegate.write(src);
    }
    @Override public MappedByteBuffer map(MapMode mode, long position, long size) {
        throw new UnsupportedOperationException();
    }
    @Override public FileLock lock(long position, long size, boolean shared) { return null; }
    @Override public FileLock tryLock(long position, long size, boolean shared) { return null; }
    @Override protected void implCloseChannel() throws IOException { delegate.close(); }
}
