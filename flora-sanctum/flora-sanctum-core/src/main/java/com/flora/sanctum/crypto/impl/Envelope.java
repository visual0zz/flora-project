package com.flora.sanctum.crypto.impl;

/**
 * 块信封常量（见设计 02"对象信封格式"）。
 */
public final class Envelope {

    private Envelope() {
    }

    /** magic 4 字节 = 0x87 0xC2 0x55 0xAD（随机生成，固定）。 */
    public static final byte[] MAGIC = {(byte) 0x87, (byte) 0xC2, (byte) 0x55, (byte) 0xAD};

    /** 信封头长度：magic(4)+version(1)+flags(1)+uuid(16)+keyId(4)+nonce(12)。 */
    public static final int HEADER_LEN = 4 + 1 + 1 + 16 + 4 + 12;

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
