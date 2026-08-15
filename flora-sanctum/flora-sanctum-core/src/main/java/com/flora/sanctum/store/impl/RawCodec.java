package com.flora.sanctum.store.impl;

import com.flora.sanctum.store.Codec;

/**
 * 恒等 Codec：不加密也不解密（裸字节直通）。
 * <p>
 * 用于已由 {@link com.flora.sanctum.crypto.impl.CipherCodec} 加密成块的字节直接落盘。
 */
public final class RawCodec implements Codec {

    @Override
    public byte[] encode(byte[] data) {
        return data;
    }

    @Override
    public byte[] decode(byte[] data) {
        return data;
    }
}
