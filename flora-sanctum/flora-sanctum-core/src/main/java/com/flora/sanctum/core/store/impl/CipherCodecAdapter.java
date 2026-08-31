package com.flora.sanctum.core.store.impl;

import com.flora.sanctum.core.crypto.impl.CipherCodec;
import com.flora.sanctum.core.store.Codec;

import java.util.UUID;

/**
 * 将 {@link CipherCodec} 适配为存储层 {@link Codec}。
 * <p>
 * encode：用 CipherCodec 加密对象负载成标准块（信封原始字节），并返回落盘字节。
 * decode：GCM-SIV 解密并验证 tag。对象 uuid 不写入信封头，而在构造时注入后于加解密两侧
 * 参与 AAD（{@code uuid ‖ 时间戳 ‖ 信封头}）；文件块的 uuid 由块文件路径反推。
 * keyId 由 {@link CipherCodec#encode} 内部生成（nonce 随机 + KeyIdDeriver 派生）。
 */
public final class CipherCodecAdapter implements Codec {

    private final CipherCodec codec;
    private final UUID objectUuid;

    public CipherCodecAdapter(CipherCodec codec, UUID objectUuid) {
        this.codec = codec;
        this.objectUuid = objectUuid;
    }

    @Override
    public byte[] encode(byte[] data, String timestamp) {
        // keyId 由 CipherCodec 内部生成（nonce 随机 + KeyIdDeriver 派生）
        return codec.encode(objectUuid, data, timestamp);
    }

    @Override
    public byte[] decode(byte[] data, String timestamp) {
        return codec.decode(data, objectUuid, timestamp);
    }
}
