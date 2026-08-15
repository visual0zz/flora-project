package com.flora.sanctum.crypto;

import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMSIVBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * 块信封编解码（见设计 02/04b）。
 * <p>
 * 密文块格式（加密时真实值）：
 * {@code magic(4)+version(1)+flags(1)+uuid(16)+keyId(4)+nonce(16)+ciphertext+tag(16)}。
 * <p>
 * AAD = 整个信封头（magic‖version‖flags‖uuid‖keyId‖nonce），GCM-SIV tag 全量认证。
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
     * @param uuid       对象 UUID
     * @param plaintext  明文负载
     * @param keyId      keyId（4 字节），由调用方提供（DEK 派生）
     */
    public byte[] encode(UUID uuid, byte[] plaintext, byte[] keyId) {
        if (keyId.length != 4) {
            throw new IllegalArgumentException("keyId must be 4 bytes");
        }
        byte[] nonce = new byte[Envelope.NONCE_LEN];
        random.nextBytes(nonce);

        // 信封头（真实值，用于 AAD）
        byte[] header = new byte[Envelope.HEADER_LEN];
        System.arraycopy(Envelope.MAGIC, 0, header, 0, 4);
        header[4] = Envelope.VERSION_1;
        header[5] = Envelope.FLAG_CIPHER;
        writeUuid(header, 6, uuid);
        System.arraycopy(keyId, 0, header, 22, 4);
        System.arraycopy(nonce, 0, header, 26, Envelope.NONCE_LEN);

        // GCM-SIV 加密，AAD = header
        GCMSIVBlockCipher cipher = new GCMSIVBlockCipher(new AESEngine());
        CipherParameters params = new AEADParameters(new KeyParameter(encKey), Envelope.TAG_LEN * 8, nonce, header);
        cipher.init(true, params);
        byte[] ct = new byte[cipher.getOutputSize(plaintext.length)];
        int n = cipher.processBytes(plaintext, 0, plaintext.length, ct, 0);
        try {
            n += cipher.doFinal(ct, n);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("encrypt failed", e);
        }
        // ct 实际含 ciphertext + tag（GCM 模式末尾附加 tag）
        int ctLen = n;
        byte[] tag = new byte[Envelope.TAG_LEN];
        System.arraycopy(ct, ctLen - Envelope.TAG_LEN, tag, 0, Envelope.TAG_LEN);

        // 拼完整真实块：header + ciphertext + tag
        byte[] block = new byte[header.length + ctLen];
        System.arraycopy(header, 0, block, 0, header.length);
        System.arraycopy(ct, 0, block, header.length, ctLen);

        return obfuscate(block);
    }

    /**
     * 解码并解密落盘块（含随机异或混淆）。返回 [uuid, plaintext]。
     */
    public DecodedBlock decode(byte[] obfuscated) {
        byte[] block = deobfuscate(obfuscated);
        if (block.length < Envelope.HEADER_LEN) {
            throw new IllegalArgumentException("block too short");
        }
        // 校验 magic
        for (int i = 0; i < 4; i++) {
            if (block[i] != Envelope.MAGIC[i]) {
                throw new IllegalArgumentException("bad magic");
            }
        }
        if (block[4] != Envelope.VERSION_1) {
            throw new IllegalArgumentException("unsupported version");
        }
        if (block[5] != Envelope.FLAG_CIPHER) {
            throw new IllegalArgumentException("not a cipher block");
        }
        UUID uuid = readUuid(block, 6);
        byte[] nonce = new byte[Envelope.NONCE_LEN];
        System.arraycopy(block, 26, nonce, 0, Envelope.NONCE_LEN);
        byte[] header = new byte[Envelope.HEADER_LEN];
        System.arraycopy(block, 0, header, 0, Envelope.HEADER_LEN);

        byte[] ciphertext = new byte[block.length - Envelope.HEADER_LEN];
        System.arraycopy(block, Envelope.HEADER_LEN, ciphertext, 0, ciphertext.length);

        GCMSIVBlockCipher cipher = new GCMSIVBlockCipher(new AESEngine());
        CipherParameters params = new AEADParameters(new KeyParameter(encKey), Envelope.TAG_LEN * 8, nonce, header);
        cipher.init(false, params);
        byte[] out = new byte[cipher.getOutputSize(ciphertext.length)];
        int n = cipher.processBytes(ciphertext, 0, ciphertext.length, out, 0);
        try {
            n += cipher.doFinal(out, n);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("decrypt failed: authentication failed", e);
        }
        byte[] plain = new byte[n];
        System.arraycopy(out, 0, plain, 0, n);
        return new DecodedBlock(uuid, plain);
    }

    /** 加密时使用，生成 keyId = byte1 ‖ SHA256(DEK‖byte1)[0:3]。 */
    public byte[] makeKeyId() {
        byte[] byte1 = new byte[1];
        random.nextBytes(byte1);
        byte[] hash = sha256(concat(dek, byte1));
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
