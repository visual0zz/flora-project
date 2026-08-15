package com.flora.sanctum.crypto;

import java.util.List;

/**
 * 块解密解析器：结合 keyId 索引与 GCM-SIV 试解。
 * <p>
 * 读取某块时，用其信封头 keyId 查 {@link KeyIdIndex} 得候选 DEK 集合
 * （通常 1 个，跨 DEK 碰撞时少数个），逐个构造 {@link CipherCodec} 试解；
 * GCM-SIV tag 验证通过者即确证；全部失败视为该块解密失败。
 */
public final class BlockResolver {

    private final KeyIdIndex index;

    public BlockResolver(KeyIdIndex index) {
        this.index = index;
    }

    /**
     * 解析一个密文块（含随机异或混淆的落盘字节）。
     *
     * @return 解密负载；候选 DEK 全部试解失败则返回 {@code null}（调用方按"非本库可解"处理）。
     */
    public byte[] decode(byte[] obfuscatedBlock) {
        // 先解异或 + 读 keyId（信封头偏移 22）
        byte[] block = deobfuscate(obfuscatedBlock);
        if (block.length < 26) {
            throw new IllegalArgumentException("block too short");
        }
        for (int i = 0; i < 4; i++) {
            if (block[i] != Envelope.MAGIC[i]) {
                throw new IllegalArgumentException("bad magic");
            }
        }
        if (block[5] != Envelope.FLAG_CIPHER) {
            // 明文块不在此解析
            return null;
        }
        byte[] keyId = new byte[4];
        System.arraycopy(block, 22, keyId, 0, 4);

        List<byte[]> candidates = index.lookup(keyId);
        for (byte[] dek : candidates) {
            byte[] encKey = deriveEncKey(dek);
            CipherCodec codec = new CipherCodec(encKey, dek);
            try {
                return codec.decode(obfuscatedBlock).plaintext;
            } catch (IllegalStateException e) {
                // tag 验证失败 → 试下一个候选
            }
        }
        return null;
    }

    private static byte[] deriveEncKey(byte[] dek) {
        try {
            return com.flora.sanctum.crypto.impl.HkdfSha256.derive(dek, null, "sanctum-enc", 32);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] deobfuscate(byte[] in) {
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
}
