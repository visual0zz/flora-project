package com.flora.sanctum.core.store;

import com.flora.sanctum.core.crypto.impl.Envelope;

/**
 * 块级信封识别（存储层）：magic 匹配与最小长度校验。
 * <p>
 * 存储层不解析负载语义，只做块边界识别（见设计 04b"块自描述"）。
 * 头部字段（nonce、keyId）的提取在 {@link com.flora.sanctum.core.crypto.impl.CipherCodec} 与
 * {@link com.flora.sanctum.core.crypto.impl.BlockResolver} 内完成；对象 uuid 不存于头部，
 * 由块文件路径反推（见 {@link Block#uuid()}）。
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
}
