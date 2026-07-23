package com.flora.classfile;

import java.util.ArrayList;
import java.util.List;


/**
 * 字节数组构建器，以分块方式高效构建字节数组。
 * <p>支持按大端序写入 byte、short、int、long 及字节数组，
 * 最终通过 {@link #toByteArray()} 合并为单一数组。避免频繁的数组拷贝和扩容。</p>
 */
final class ByteArrayBuilder {

    private final List<byte[]> chunks = new ArrayList<>();
    private int size = 0;

    /**
     * 写入一个字节的低 8 位。
     *
     * @param v 要写入的值（仅低 8 位有效）
     */
    void writeByte(int v) {
        chunks.add(new byte[]{(byte) (v & 0xff)});
        size += 1;
    }

    /**
     * 以大端序写入两个字节（short）。
     *
     * @param v 要写入的值（仅低 16 位有效）
     */
    void writeShort(int v) {
        chunks.add(new byte[]{(byte) ((v >>> 8) & 0xff), (byte) (v & 0xff)});
        size += 2;
    }

    /**
     * 以大端序写入四个字节（int）。
     *
     * @param v 要写入的 int 值
     */
    void writeInt(int v) {
        chunks.add(new byte[]{
                (byte) ((v >>> 24) & 0xff),
                (byte) ((v >>> 16) & 0xff),
                (byte) ((v >>> 8) & 0xff),
                (byte) (v & 0xff)});
        size += 4;
    }

    /**
     * 以大端序写入八个字节（long）。
     *
     * @param v 要写入的 long 值
     */
    void writeLong(long v) {
        chunks.add(new byte[]{
                (byte) ((v >>> 56) & 0xff),
                (byte) ((v >>> 48) & 0xff),
                (byte) ((v >>> 40) & 0xff),
                (byte) ((v >>> 32) & 0xff),
                (byte) ((v >>> 24) & 0xff),
                (byte) ((v >>> 16) & 0xff),
                (byte) ((v >>> 8) & 0xff),
                (byte) (v & 0xff)});
        size += 8;
    }

    /**
     * 写入一个完整的字节数组。
     *
     * @param data 要写入的字节数组
     */
    void write(byte[] data) {
        chunks.add(data);
        size += data.length;
    }

    /**
     * 将另一个 ByteArrayBuilder 的全部内容追加到当前构建器中。
     *
     * @param other 另一个 ByteArrayBuilder 实例
     */
    void write(ByteArrayBuilder other) {
        chunks.addAll(other.chunks);
        size += other.size;
    }

    /**
     * @return 当前已写入的总字节数
     */
    int size() {
        return size;
    }

    /**
     * 将所有分块合并为一个完整的字节数组。
     *
     * @return 合并后的字节数组
     */
    byte[] toByteArray() {
        byte[] result = new byte[size];
        int pos = 0;
        for (byte[] c : chunks) {
            System.arraycopy(c, 0, result, pos, c.length);
            pos += c.length;
        }
        return result;
    }
}
