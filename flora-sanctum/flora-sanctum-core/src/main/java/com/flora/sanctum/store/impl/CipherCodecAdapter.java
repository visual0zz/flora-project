package com.flora.sanctum.store.impl;

import com.flora.sanctum.crypto.impl.CipherCodec;
import com.flora.sanctum.store.Codec;

import java.util.UUID;

/**
 * 将 {@link CipherCodec} 适配为存储层 {@link Codec}。
 * <p>
 * encode：用 CipherCodec 加密对象负载成标准块（含随机异或混淆），并返回落盘字节。
 * decode：解异或 + GCM-SIV 解密。注意 decode 需要 uuid（作为 encode 时的对象身份），
 * 由 {@link #encode} 内部生成的 keyId 与 uuid 共同决定；此处 decode 依赖调用方
 * 传入对象 uuid。
 */
public final class CipherCodecAdapter implements Codec {

    private final CipherCodec codec;
    private final UUID objectUuid;

    public CipherCodecAdapter(CipherCodec codec, UUID objectUuid) {
        this.codec = codec;
        this.objectUuid = objectUuid;
    }

    @Override
    public byte[] encode(byte[] data) {
        byte[] keyId = codec.makeKeyId();
        return codec.encode(objectUuid, data, keyId);
    }

    @Override
    public byte[] decode(byte[] data) {
        CipherCodec.DecodedBlock d = codec.decode(data);
        return d.plaintext;
    }
}
