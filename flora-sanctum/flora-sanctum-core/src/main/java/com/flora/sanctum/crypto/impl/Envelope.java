package com.flora.sanctum.crypto.impl;

/**
 * 块信封常量（见设计 02"对象信封格式"）。
 */
public final class Envelope {

    private Envelope() {
    }

    /** magic 8 字节 ASCII = "Zhao zhi"（'Z','h','a','o',' ','z','h','i'），固定。 */
    public static final byte[] MAGIC = {'Z', 'h', 'a', 'o', ' ', 'z', 'h', 'i'};
    public static final int MAGIC_LEN = 8;

    /** 明文块头长度：magic(8)+version(1)+flags(1)+uuid(16) = 26。 */
    public static final int PLAINTEXT_HEADER_LEN = MAGIC_LEN + 1 + 1 + 16;

    /** 信封头长度（含 keyId/nonce）：magic(8)+version(1)+flags(1)+uuid(16)+keyId(4)+nonce(12) = 42。 */
    public static final int HEADER_LEN = MAGIC_LEN + 1 + 1 + 16 + 4 + 12;

    /** 版本 1：cryptoVersion "gcm-siv-1"。 */
    public static final byte VERSION_1 = 1;

    /** flags：密文。 */
    public static final byte FLAG_CIPHER = 0x01;
    /** flags：明文。 */
    public static final byte FLAG_PLAINTEXT = 0x02;

    /** GCM-SIV tag 长度（128 位）。 */
    public static final int TAG_LEN = 16;

    /** nonce 长度（96 位随机，BC AES-GCM-SIV 要求 12 字节）。 */
    public static final int NONCE_LEN = 12;
}
