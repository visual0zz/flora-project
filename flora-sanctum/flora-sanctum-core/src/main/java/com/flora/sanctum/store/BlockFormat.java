package com.flora.sanctum.store;

/**
 * 块信封格式常量（存储层公开；crypto.impl.Envelope 与之保持一致）。
 * <p>
 * magic = 8 字节 ASCII "Zhao zhi"；明文块头不含 keyId/nonce，
 * payload 从 {@link #PLAINTEXT_HEADER_LEN} 开始（见设计 02"对象信封格式"）。
 */
public final class BlockFormat {

    private BlockFormat() {
    }

    /** magic 8 字节 ASCII "Zhao zhi"。 */
    public static final byte[] MAGIC = {'Z', 'h', 'a', 'o', ' ', 'z', 'h', 'i'};
    public static final int MAGIC_LEN = 8;

    /** 明文块头长度：magic(8)+version(1)+flags(1)+uuid(16) = 26。 */
    public static final int PLAINTEXT_HEADER_LEN = MAGIC_LEN + 1 + 1 + 16;

    /** 密文块头长度（含 keyId/nonce）：= 42。 */
    public static final int HEADER_LEN = MAGIC_LEN + 1 + 1 + 16 + 4 + 12;

    public static final byte VERSION_1 = 1;
    public static final byte FLAG_CIPHER = 0x01;
    public static final byte FLAG_PLAINTEXT = 0x02;

    /** GCM-SIV tag 长度（128 位）。 */
    public static final int TAG_LEN = 16;
    /** nonce 长度（96 位随机）。 */
    public static final int NONCE_LEN = 12;
}
