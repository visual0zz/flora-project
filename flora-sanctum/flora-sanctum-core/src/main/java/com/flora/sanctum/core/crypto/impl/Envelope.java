package com.flora.sanctum.core.crypto.impl;

import com.flora.sanctum.core.store.BlockFormat;

/**
 * 块信封常量（crypto 内部兼容引用）。
 * <p>
 * 常量的单一事实来源是 {@link com.flora.sanctum.core.store.BlockFormat}（存储层公开），
 * 本类仅转发，避免信封格式两处定义不同步。
 */
public final class Envelope {

    private Envelope() {
    }

    public static final byte[] MAGIC = BlockFormat.MAGIC;
    public static final int MAGIC_LEN = BlockFormat.MAGIC_LEN;
    public static final int PLAINTEXT_HEADER_LEN = BlockFormat.PLAINTEXT_HEADER_LEN;
    public static final int HEADER_LEN = BlockFormat.HEADER_LEN;
    public static final byte VERSION_1 = BlockFormat.VERSION_1;
    public static final byte FLAG_CIPHER = BlockFormat.FLAG_CIPHER;
    public static final byte FLAG_PLAINTEXT = BlockFormat.FLAG_PLAINTEXT;
    public static final int TAG_LEN = BlockFormat.TAG_LEN;
    public static final int MANIFEST_MAC_LEN = BlockFormat.MANIFEST_MAC_LEN;
    public static final int NONCE_LEN = BlockFormat.NONCE_LEN;
    public static final int KEYID_LEN = BlockFormat.KEYID_LEN;
}
