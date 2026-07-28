package com.flora.os.virtual.file.nio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/**
 * 简易 {@link SeekableByteChannel} 实现，包装 {@code byte[]} 或 {@link OutputStream}。
 */
final class VfsByteChannel implements SeekableByteChannel {

    private byte[] data;
    private final boolean writeOnly;
    private final OutputStream out;
    private int pos;
    private boolean open = true;

    /** 读模式：包装字节数组。 */
    VfsByteChannel(byte[] data, boolean writeOnly) {
        this.data = data;
        this.writeOnly = writeOnly;
        this.out = null;
    }

    /** 写模式：包装 OutputStream。 */
    VfsByteChannel(OutputStream out) {
        this.data = new byte[0];
        this.writeOnly = true;
        this.out = out;
    }

    @Override public boolean isOpen() { return open; }

    @Override public void close() throws IOException {
        open = false;
        if (out != null) out.close();
    }

    @Override public int read(ByteBuffer dst) throws IOException {
        if (writeOnly) throw new IOException("通道未打开读");
        int remaining = data.length - pos;
        if (remaining <= 0) return -1;
        int toRead = Math.min(dst.remaining(), remaining);
        dst.put(data, pos, toRead);
        pos += toRead;
        return toRead;
    }

    @Override public int write(ByteBuffer src) throws IOException {
        if (out == null) throw new IOException("通道未打开写");
        int len = src.remaining();
        byte[] buf = new byte[len];
        src.get(buf);
        out.write(buf);
        pos += len;
        return len;
    }

    @Override public long position() { return pos; }

    @Override public SeekableByteChannel position(long newPosition) {
        pos = (int) newPosition;
        return this;
    }

    @Override public long size() { return data != null ? data.length : pos; }

    @Override public SeekableByteChannel truncate(long size) {
        if (size < data.length) {
            byte[] trimmed = new byte[(int) size];
            System.arraycopy(data, 0, trimmed, 0, (int) size);
            data = trimmed;
        }
        return this;
    }
}
