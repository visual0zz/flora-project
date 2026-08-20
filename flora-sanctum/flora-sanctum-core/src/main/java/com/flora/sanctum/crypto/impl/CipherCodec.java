package com.flora.sanctum.crypto.impl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * 块信封编解码（见设计 02/04b）。
 * <p>
 * 密文块格式（加密时真实值）：
 * {@code magic(8)+version(1)+flags(1)+uuid(16)+keyId(4)+nonce(12)+ciphertext+tag(16)}。
 * <p>
 * AAD = 整个信封头（magic‖version‖flags‖uuid‖keyId‖nonce）‖ 块级时间戳（十进制 UTF-8），
 * GCM-SIV tag 全量认证。明文负载先 deflate 压缩再加密（降低冗余与长度暴露）。
 * 时间戳不存于负载 JSON，由块前缀 {@code timestamp:base58} 提供，解码时经参数传入以重建 AAD。
 * 落盘/读盘时，整个字节序列与每块随机 xorByte 逐字节异或；xorByte 不落盘，
 * 读取时从落盘首字节反推：{@code xorByte = bytes[0] ^ MAGIC[0]}。
 */
public final class CipherCodec {

    private final byte[] encKey;
    private final byte[] dek;
    private final SecureRandomSource random;

    public CipherCodec(byte[] encKey, byte[] dek) {
        this(encKey, dek, new SecureRandomSource());
    }

    public CipherCodec(byte[] encKey, byte[] dek, SecureRandomSource random) {
        this.encKey = encKey.clone();
        this.dek = dek.clone();
        this.random = random;
    }

    /**
     * 加密对象负载，返回含随机异或混淆的落盘块字节（含完整信封 + tag）。
     *
     * @param uuid        对象 UUID
     * @param plaintext   明文负载（将被 deflate 压缩后加密）
     * @param keyId       keyId（4 字节），由调用方提供（DEK 派生）
     * @param timestamp   块级时间戳（进入 AAD 认证）
     */
    public byte[] encode(UUID uuid, byte[] plaintext, byte[] keyId, long timestamp) {
        if (keyId.length != 4) {
            throw new IllegalArgumentException("keyId must be 4 bytes");
        }
        byte[] nonce = new byte[Envelope.NONCE_LEN];
        random.nextBytes(nonce);

        // 信封头（真实值，用于 AAD）
        byte[] header = new byte[Envelope.HEADER_LEN];
        System.arraycopy(Envelope.MAGIC, 0, header, 0, Envelope.MAGIC_LEN);
        header[Envelope.MAGIC_LEN] = Envelope.VERSION_1;
        header[Envelope.MAGIC_LEN + 1] = Envelope.FLAG_CIPHER;
        int uuidOff = Envelope.MAGIC_LEN + 2;
        writeUuid(header, uuidOff, uuid);
        int keyIdOff = uuidOff + 16;
        System.arraycopy(keyId, 0, header, keyIdOff, 4);
        int nonceOff = keyIdOff + 4;
        System.arraycopy(nonce, 0, header, nonceOff, Envelope.NONCE_LEN);

        // AAD = 信封头 ‖ 时间戳
        byte[] aad = concat(header, Long.toString(timestamp).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        byte[] compressed = deflate(plaintext);

        // GCM-SIV 加密，AAD = aad，输出 = 密文 ‖ tag
        byte[] encrypted = GcmSiv.encrypt(encKey, nonce, aad, compressed);

        // 拼完整真实块：header + ciphertext + tag
        byte[] block = new byte[header.length + encrypted.length];
        System.arraycopy(header, 0, block, 0, header.length);
        System.arraycopy(encrypted, 0, block, header.length, encrypted.length);

        return obfuscate(block);
    }

    /**
     * 解码并解密落盘块（含随机异或混淆）。返回 [uuid, plaintext]。
     *
     * @param obfuscated 落盘块字节
     * @param timestamp  块级时间戳（重建 AAD 认证）
     */
    public DecodedBlock decode(byte[] obfuscated, long timestamp) {
        byte[] block = deobfuscate(obfuscated);
        if (block.length < Envelope.HEADER_LEN) {
            throw new IllegalArgumentException("block too short");
        }
        // 校验 magic
        for (int i = 0; i < Envelope.MAGIC_LEN; i++) {
            if (block[i] != Envelope.MAGIC[i]) {
                throw new IllegalArgumentException("bad magic");
            }
        }
        if (block[Envelope.MAGIC_LEN] != Envelope.VERSION_1) {
            throw new IllegalArgumentException("unsupported version");
        }
        if (block[Envelope.MAGIC_LEN + 1] != Envelope.FLAG_CIPHER) {
            throw new IllegalArgumentException("not a cipher block");
        }
        int uuidOff = Envelope.MAGIC_LEN + 2;
        int keyIdOff = uuidOff + 16;
        int nonceOff = keyIdOff + 4;
        UUID uuid = readUuid(block, uuidOff);
        byte[] nonce = new byte[Envelope.NONCE_LEN];
        System.arraycopy(block, nonceOff, nonce, 0, Envelope.NONCE_LEN);
        byte[] header = new byte[Envelope.HEADER_LEN];
        System.arraycopy(block, 0, header, 0, Envelope.HEADER_LEN);

        byte[] ciphertext = new byte[block.length - Envelope.HEADER_LEN];
        System.arraycopy(block, Envelope.HEADER_LEN, ciphertext, 0, ciphertext.length);

        byte[] aad = concat(header, Long.toString(timestamp).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        // GCM-SIV 解密并验证 tag（认证失败抛 IllegalStateException，与调用方契约一致）
        final byte[] compressed;
        try {
            compressed = GcmSiv.decrypt(encKey, nonce, aad, ciphertext);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("decrypt failed: authentication failed", e);
        }
        return new DecodedBlock(uuid, inflate(compressed));
    }

    /** 加密时使用，生成 keyId = byte1 ‖ SHA256(DEK‖byte1)[0:3]。 */
    public byte[] makeKeyId() {
        return makeKeyIdWith(dek);
    }

    /** 用任意密钥材料生成 keyId（KEK 包裹的根 group 用 KEK；文件夹 DEK 用 DEK）。 */
    public byte[] makeKeyIdWith(byte[] keyMaterial) {
        byte[] byte1 = new byte[1];
        random.nextBytes(byte1);
        byte[] hash = sha256(concat(keyMaterial, byte1));
        byte[] keyId = new byte[4];
        keyId[0] = byte1[0];
        System.arraycopy(hash, 0, keyId, 1, 3);
        return keyId;
    }

    private byte[] obfuscate(byte[] block) {
        byte xor = random.nextByte();
        byte[] out = new byte[block.length];
        for (int i = 0; i < block.length; i++) {
            out[i] = (byte) (block[i] ^ xor);
        }
        return out;
    }

    private byte[] deobfuscate(byte[] in) {
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

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    /** deflate 压缩明文（RFC 1951 原始流）。 */
    private static byte[] deflate(byte[] in) {
        java.util.zip.Deflater def = new java.util.zip.Deflater();
        def.setInput(in);
        def.finish();
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        while (!def.finished()) {
            int n = def.deflate(buf);
            out.write(buf, 0, n);
        }
        def.end();
        return out.toByteArray();
    }

    /** inflate 解压（对应 deflate）。 */
    private static byte[] inflate(byte[] in) {
        java.util.zip.Inflater inf = new java.util.zip.Inflater();
        inf.setInput(in);
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0) {
                    break;
                }
                out.write(buf, 0, n);
            }
        } catch (java.util.zip.DataFormatException e) {
            throw new IllegalStateException("inflate failed", e);
        } finally {
            inf.end();
        }
        return out.toByteArray();
    }

    private static byte[] sha256(byte[] in) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(in);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeUuid(byte[] dst, int off, UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(dst, off, 16).order(ByteOrder.BIG_ENDIAN);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(byte[] src, int off) {
        ByteBuffer bb = ByteBuffer.wrap(src, off, 16).order(ByteOrder.BIG_ENDIAN);
        return new UUID(bb.getLong(), bb.getLong());
    }

    /** 解码结果。 */
    public static final class DecodedBlock {
        public final UUID uuid;
        public final byte[] plaintext;

        public DecodedBlock(UUID uuid, byte[] plaintext) {
            this.uuid = uuid;
            this.plaintext = plaintext;
        }
    }
}
