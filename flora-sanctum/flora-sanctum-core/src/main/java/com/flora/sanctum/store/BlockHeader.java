package com.flora.sanctum.store;

import com.flora.sanctum.crypto.impl.Envelope;

/**
 * 块级信封操作（存储层）：magic 识别、头部字段提取。
 * <p>
 * 存储层不解析负载语义，只做块边界识别与头部读取（见设计 04b"块自描述"）。
 */
public final class BlockHeader {

    private BlockHeader() {
    }

    /** 判断字节序列是否为合法块（magic 匹配且长度足够）。 */
    public static boolean isBlock(byte[] block) {
        if (block.length < Envelope.HEADER_LEN) {
            return false;
        }
        for (int i = 0; i < Envelope.MAGIC_LEN; i++) {
            if (block[i] != Envelope.MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /** 从块读取 uuid（偏移 magic_len+2，16 字节）。 */
    public static java.util.UUID uuid(byte[] block) {
        if (!isBlock(block)) {
            throw new IllegalArgumentException("not a block");
        }
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(block, Envelope.MAGIC_LEN + 2, 16)
                .order(java.nio.ByteOrder.BIG_ENDIAN);
        return new java.util.UUID(bb.getLong(), bb.getLong());
    }
}
