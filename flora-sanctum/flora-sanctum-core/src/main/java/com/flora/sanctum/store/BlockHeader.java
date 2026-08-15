package com.flora.sanctum.store;

import com.flora.sanctum.crypto.Envelope;

/**
 * 块级信封操作（存储层）：随机异或混淆的解/混淆、magic 识别、头部字段提取。
 * <p>
 * 存储层不解析负载语义，只做块边界识别与头部读取（见设计 04b"块自描述"）。
 */
public final class BlockHeader {

    private BlockHeader() {
    }

    /** 随机异或混淆。 */
    public static byte[] obfuscate(byte[] block, byte xor) {
        byte[] out = new byte[block.length];
        for (int i = 0; i < block.length; i++) {
            out[i] = (byte) (block[i] ^ xor);
        }
        return out;
    }

    /** 解随机异或混淆：xorByte 不落盘，用 magic[0] 反推。 */
    public static byte[] deobfuscate(byte[] in) {
        if (in.length == 0) {
            throw new IllegalArgumentException("empty");
        }
        byte xor = (byte) (in[0] ^ Envelope.MAGIC[0]);
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (byte) (in[i] ^ xor);
        }
        return out;
    }

    /** 判断解异或后的字节是否为合法块（magic 匹配且长度足够）。 */
    public static boolean isBlock(byte[] deobfuscated) {
        if (deobfuscated.length < Envelope.HEADER_LEN) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (deobfuscated[i] != Envelope.MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /** 从解异或后的块读取 uuid 偏移 6（16 字节）。 */
    public static java.util.UUID uuid(byte[] deobfuscated) {
        if (!isBlock(deobfuscated)) {
            throw new IllegalArgumentException("not a block");
        }
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(deobfuscated, 6, 16)
                .order(java.nio.ByteOrder.BIG_ENDIAN);
        return new java.util.UUID(bb.getLong(), bb.getLong());
    }

    /** 从解异或后的块读取 keyId 偏移 22（4 字节，仅密文块）。 */
    public static byte[] keyId(byte[] deobfuscated) {
        if (!isBlock(deobfuscated)) {
            throw new IllegalArgumentException("not a block");
        }
        byte[] keyId = new byte[4];
        System.arraycopy(deobfuscated, 22, keyId, 0, 4);
        return keyId;
    }
}
