package com.flora.sanctum.crypto.impl;

import com.flora.sanctum.crypto.KeyIdDeriver;

import java.util.List;
import java.util.function.Supplier;

/**
 * 块解密解析器：结合 keyId 派生索引与 GCM-SIV 试解（见设计"keyId 防关联"）。
 * <p>
 * 读取某块时，从密文头读 (nonce, keyId)，用 {@link KeyIdDeriver#resolveDekId} 恢复内部标识
 * dekId（对合可逆），查 {@link KeyIdIndex} 得候选 DEK 集合（通常 1 个，跨 DEK 碰撞时少数个），
 * 逐个构造 {@link CipherCodec} 试解；GCM-SIV tag 验证通过者即确证；全部失败视为该块解密失败。
 * <p>
 * 内部存储与外部加密数据同一机制（同头结构、同 keyId 派生、同定位路径）。
 */
public final class BlockResolver {

    private final KeyIdIndex index;
    private final Supplier<byte[]> repoKeyIdSeed;

    /** @param repoKeyIdSeed 仓库级 keyId 派生种子提供者（解锁后非 null；旧库 null 时无法定位返回 null） */
    public BlockResolver(KeyIdIndex index, Supplier<byte[]> repoKeyIdSeed) {
        this.index = index;
        this.repoKeyIdSeed = repoKeyIdSeed;
    }

    /**
     * 解析一个密文块（含随机异或混淆的落盘字节）。
     *
     * @param obfuscatedBlock 落盘块字节
     * @param timestamp       块级时间戳（规范 ASCII 十进制字符串，落盘前缀原文，重建 AAD）
     * @return 解密负载；候选 DEK 全部试解失败或无法定位返回 {@code null}。
     */
    public byte[] decode(byte[] obfuscatedBlock, String timestamp) {
        byte[] block = deobfuscate(obfuscatedBlock);
        if (block.length < Envelope.HEADER_LEN) {
            throw new IllegalArgumentException("block too short");
        }
        for (int i = 0; i < Envelope.MAGIC_LEN; i++) {
            if (block[i] != Envelope.MAGIC[i]) {
                throw new IllegalArgumentException("bad magic");
            }
        }
        if (block[Envelope.MAGIC_LEN + 1] != Envelope.FLAG_CIPHER) {
            return null; // 明文块不在此解析
        }
        int nonceOff = Envelope.MAGIC_LEN + 2 + 16;
        byte[] nonce = new byte[Envelope.NONCE_LEN];
        System.arraycopy(block, nonceOff, nonce, 0, Envelope.NONCE_LEN);
        byte[] keyId = new byte[Envelope.KEYID_LEN];
        System.arraycopy(block, nonceOff + Envelope.NONCE_LEN, keyId, 0, Envelope.KEYID_LEN);

        byte[] repoSeed = repoKeyIdSeed.get();
        if (repoSeed == null) {
            return null; // 无仓库级派生种子（旧库未补种），无法定位
        }
        byte[] dekId = KeyIdDeriver.resolveDekId(repoSeed, nonce, keyId);
        List<byte[]> candidates = index.lookup(dekId);
        for (byte[] dek : candidates) {
            byte[] encKey = deriveEncKey(dek);
            CipherCodec codec = new CipherCodec(encKey, dek);
            try {
                return codec.decode(obfuscatedBlock, timestamp).plaintext;
            } catch (IllegalStateException e) {
                // tag 验证失败 → 试下一个候选
            }
        }
        return null;
    }

    private static byte[] deriveEncKey(byte[] dek) {
        try {
            return com.flora.sanctum.crypto.KeyDerivation.encKey(dek);
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
