package com.flora.sanctum.core.store;

/**
 * 块信封格式常量（存储层公开；crypto.impl.Envelope 与之保持一致）。
 * <p>
 * magic 为固定 6 字节魔数（见 {@link #MAGIC}）。version 字段统一为 1（VERSION_1，第一代格式），
 * 块类型由 flags 区分（FLAG_CIPHER / FLAG_PLAINTEXT）。
 * <ul>
 *   <li><b>明文块</b>（仅 manifest）：头 = magic(6)+version(1)+flags(1)+uuid(16)，
 *       payload 从 {@link #PLAINTEXT_HEADER_LEN} 开始，尾附 MAC（见 {@link com.flora.sanctum.core.model.impl.ManifestStore}）。</li>
 *   <li><b>密文块</b>：头 = magic(6)+version(1)+flags(1)+uuid(16)+nonce(12)+keyId(8)，
 *       nonce 置于 keyId 前（解析时先读 nonce 作 keyId 派生的 seed）。内部存储与外部加密数据
 *       用同一格式（见设计"keyId 防关联"）。</li>
 * </ul>
 */
public final class BlockFormat {

    private BlockFormat() {
    }

    /** 固定魔数（6 字节，直接存储字节编码）。 */
    public static final byte[] MAGIC = {(byte) 0xBD, (byte) 0xE0, (byte) 0xE0, (byte) 0xB3, (byte) 0xE7, (byte) 0xEE};
    public static final int MAGIC_LEN = 6;

    /** 明文块头长度：magic(6)+version(1)+flags(1)+uuid(16) = 24。 */
    public static final int PLAINTEXT_HEADER_LEN = MAGIC_LEN + 1 + 1 + 16;

    /** GCM-SIV tag 长度（128 位）。 */
    public static final int TAG_LEN = 16;
    /** manifest 明文块 MAC 长度（HMAC-SHA256 输出，256 位；尾附于负载之后，与密文 tag 位置对应）。 */
    public static final int MANIFEST_MAC_LEN = 32;
    /** nonce 长度（96 位随机；亦作为 keyId 派生 seed）。 */
    public static final int NONCE_LEN = 12;
    /** keyId 长度（64 位）。 */
    public static final int KEYID_LEN = 8;

    /** 密文块头长度：magic(6)+version(1)+flags(1)+uuid(16)+nonce(12)+keyId(8) = 44。 */
    public static final int HEADER_LEN = MAGIC_LEN + 1 + 1 + 16 + NONCE_LEN + KEYID_LEN;

    /** 格式版本（第一代；明文与密文块统一，块类型由 flags 区分）。 */
    public static final byte VERSION_1 = 1;
    public static final byte FLAG_CIPHER = 0x01;
    public static final byte FLAG_PLAINTEXT = 0x02;
}
