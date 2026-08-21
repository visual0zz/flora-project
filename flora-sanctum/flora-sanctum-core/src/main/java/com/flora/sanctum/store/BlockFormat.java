package com.flora.sanctum.store;

/**
 * 块信封格式常量（存储层公开；crypto.impl.Envelope 与之保持一致）。
 * <p>
 * magic = 8 字节 ASCII "Zhao zhi"。
 * <ul>
 *   <li><b>明文块</b>（VERSION_1，仅 manifest）：头 = magic(8)+version(1)+flags(1)+uuid(16)，
 *       payload 从 {@link #PLAINTEXT_HEADER_LEN} 开始（见设计 02"对象信封格式"）。</li>
 *   <li><b>密文块</b>（VERSION_2）：头 = magic(8)+version(1)+flags(1)+uuid(16)+nonce(12)+keyId(8)，
 *       nonce 置于 keyId 前（解析时先读 nonce 作 keyId 派生的 seed）。内部存储与外部加密数据
 *       用同一格式（见设计"keyId 防关联"）。</li>
 * </ul>
 */
public final class BlockFormat {

    private BlockFormat() {
    }

    /** magic 8 字节 ASCII "Zhao zhi"。 */
    public static final byte[] MAGIC = {'Z', 'h', 'a', 'o', ' ', 'z', 'h', 'i'};
    public static final int MAGIC_LEN = 8;

    /** 明文块头长度：magic(8)+version(1)+flags(1)+uuid(16) = 26。 */
    public static final int PLAINTEXT_HEADER_LEN = MAGIC_LEN + 1 + 1 + 16;

    /** GCM-SIV tag 长度（128 位）。 */
    public static final int TAG_LEN = 16;
    /** nonce 长度（96 位随机；亦作为 keyId 派生 seed）。 */
    public static final int NONCE_LEN = 12;
    /** keyId 长度（64 位）。 */
    public static final int KEYID_LEN = 8;

    /** 密文块头长度：magic(8)+version(1)+flags(1)+uuid(16)+nonce(12)+keyId(8) = 46。 */
    public static final int HEADER_LEN = MAGIC_LEN + 1 + 1 + 16 + NONCE_LEN + KEYID_LEN;

    /** 明文块版本（manifest）。 */
    public static final byte VERSION_1 = 1;
    /** 密文块版本（nonce 前 + keyId 8 字节）。 */
    public static final byte VERSION_2 = 2;
    public static final byte FLAG_CIPHER = 0x01;
    public static final byte FLAG_PLAINTEXT = 0x02;
}
