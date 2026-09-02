package com.flora.sanctum.core.crypto.impl;

import com.flora.sanctum.core.crypto.KeyIdDeriver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * 块信封编解码（见设计"keyId 防关联"）。
 * <p>
 * 密文块格式（VERSION_1，内部存储与外部加密数据同一结构）：
 * {@code magic(6)+version(1)+flags(1)+nonce(12)+keyId(8)+ciphertext+tag(16)}。
 * version 与明文块同为 1（块类型由 flags 区分）。
 * <p>
 * nonce 置于 keyId 前：解析时先读 nonce（作 keyId 派生的 seed），再读 keyId。
 * keyId 在 encode 内部生成（防关联随机化），经 {@link KeyIdDeriver} 对合派生，
 * 解密侧用 {@link KeyIdDeriver#resolveDekId} 从 (nonce, keyId) 恢复内部标识定位。
 * <p>
 * 对象 uuid 不写入信封头，而由调用方提供并参与 AAD：
 * {@code AAD = uuid(16B) ‖ 时间戳（规范 ASCII 十进制字符串，落盘前缀原文）‖ 整个信封头}，
 * GCM-SIV tag 全量认证。文件块的 uuid 取自块文件路径（内容即与存放位置绑定，被移动到别的路径认证失败），
 * 无文件路径的内嵌块用 {@link #EMBEDDED_UUID}。
 * 明文负载先 deflate 压缩再加密（降低冗余与长度暴露）。
 * 时间戳不存于负载 JSON，由块前缀 {@code timestamp:base58} 提供，解码时经参数传入（原文）以重建 AAD。
 * 落盘字节为信封原始字节（无额外异或混淆）。
 */
public final class CipherCodec {

    /**
     * 无文件路径的内嵌块（如 JSON 内的包裹 DEK、外部密钥 blob）进 AAD 时使用的固定 uuid。
     * 密文变体由 12 字节随机 nonce 保证，不依赖 uuid 的随机性。
     */
    public static final UUID EMBEDDED_UUID = new UUID(0x0F0E0D0C0B0A0908L, 0x0706050403020100L);

    private final byte[] encKey;
    private final byte[] dek;
    private final SecureRandomSource random;
    private final byte[] repoKeyIdSeed; // 仓库级 keyId 派生种子（null 时 keyId 用确定性 fallback，仅测试）

    public CipherCodec(byte[] encKey, byte[] dek) {
        this(encKey, dek, null, new SecureRandomSource());
    }

    public CipherCodec(byte[] encKey, byte[] dek, SecureRandomSource random) {
        this(encKey, dek, null, random);
    }

    /** @param repoKeyIdSeed 仓库级 keyId 派生种子；产品路径必须非 null（null 仅用于无定位需求的测试） */
    public CipherCodec(byte[] encKey, byte[] dek, byte[] repoKeyIdSeed, SecureRandomSource random) {
        this.encKey = encKey.clone();
        this.dek = dek.clone();
        this.random = random;
        this.repoKeyIdSeed = repoKeyIdSeed == null ? null : repoKeyIdSeed.clone();
    }

    /**
     * 加密对象负载，返回落盘块字节（信封原始字节，含完整信封 + tag）。
     * nonce 与 keyId 均在内部生成（nonce 随机；keyId 经 KeyIdDeriver 从 repoKeyIdSeed‖nonce 派生）。
     *
     * @param uuid      对象 UUID（仅进 AAD，不写入信封头）
     * @param plaintext 明文负载（将被 deflate 压缩后加密）
     * @param timestamp 块级时间戳（规范 ASCII 十进制字符串，落盘前缀原文，进入 AAD 认证）
     */
    public byte[] encode(UUID uuid, byte[] plaintext, String timestamp) {
        byte[] nonce = new byte[Envelope.NONCE_LEN];
        random.nextBytes(nonce);
        byte[] keyId = makeKeyId(nonce);

        // 信封头（不含 uuid）：magic(6)+version(1)+flags(1)+nonce(12)+keyId(8)
        byte[] header = new byte[Envelope.HEADER_LEN];
        System.arraycopy(Envelope.MAGIC, 0, header, 0, Envelope.MAGIC_LEN);
        header[Envelope.MAGIC_LEN] = Envelope.VERSION_1;
        header[Envelope.MAGIC_LEN + 1] = Envelope.FLAG_CIPHER;
        int nonceOff = Envelope.MAGIC_LEN + 2;
        System.arraycopy(nonce, 0, header, nonceOff, Envelope.NONCE_LEN);
        int keyIdOff = nonceOff + Envelope.NONCE_LEN;
        System.arraycopy(keyId, 0, header, keyIdOff, keyId.length);

        byte[] aad = aad(uuid, timestamp, header);
        byte[] compressed = deflate(plaintext);

        // GCM-SIV 加密，AAD = aad，输出 = 密文 ‖ tag
        byte[] encrypted = GcmSiv.encrypt(encKey, nonce, aad, compressed);

        // 拼完整真实块：header + ciphertext + tag
        byte[] block = new byte[header.length + encrypted.length];
        System.arraycopy(header, 0, block, 0, header.length);
        System.arraycopy(encrypted, 0, block, header.length, encrypted.length);

        return block;
    }

    /**
     * 解码并解密落盘块，返回明文负载。
     *
     * @param block     落盘块字节（信封原始字节）
     * @param uuid      对象 UUID（不存于块内；文件块由块文件路径反推，内嵌块用 {@link #EMBEDDED_UUID}）
     * @param timestamp 块级时间戳（规范 ASCII 十进制字符串，落盘前缀原文，重建 AAD 认证）
     */
    public byte[] decode(byte[] block, UUID uuid, String timestamp) {
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
        int nonceOff = Envelope.MAGIC_LEN + 2;
        byte[] nonce = new byte[Envelope.NONCE_LEN];
        System.arraycopy(block, nonceOff, nonce, 0, Envelope.NONCE_LEN);
        byte[] header = new byte[Envelope.HEADER_LEN];
        System.arraycopy(block, 0, header, 0, Envelope.HEADER_LEN);

        byte[] ciphertext = new byte[block.length - Envelope.HEADER_LEN];
        System.arraycopy(block, Envelope.HEADER_LEN, ciphertext, 0, ciphertext.length);

        byte[] aad = aad(uuid, timestamp, header);
        // GCM-SIV 解密并验证 tag（认证失败抛 IllegalStateException，与调用方契约一致）
        final byte[] compressed;
        try {
            compressed = GcmSiv.decrypt(encKey, nonce, aad, ciphertext);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("decrypt failed: authentication failed", e);
        }
        return inflate(compressed);
    }

    /** AAD 输入：uuid(16B，大端) ‖ 时间戳(ASCII 原文) ‖ 信封头。 */
    private static byte[] aad(UUID uuid, String timestamp, byte[] header) {
        byte[] ts = timestamp.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        return concat(uuidBytes(uuid), concat(ts, header));
    }

    /** uuid → 16 字节大端表示（进 AAD/MAC 输入的统一编码）。 */
    public static byte[] uuidBytes(UUID uuid) {
        byte[] out = new byte[16];
        ByteBuffer bb = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return out;
    }

    /** 生成 keyId：repoKeyIdSeed 非空时经 KeyIdDeriver（可逆、防关联）；否则确定性 fallback（仅测试）。 */
    public byte[] makeKeyId(byte[] nonce) {
        if (repoKeyIdSeed != null) {
            return KeyIdDeriver.makeKeyId(repoKeyIdSeed, nonce, dek);
        }
        byte[] hash = sha256(concat(dek, nonce));
        byte[] keyId = new byte[Envelope.KEYID_LEN];
        System.arraycopy(hash, 0, keyId, 0, keyId.length);
        return keyId;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    /** deflate 压缩明文（RFC 1951 原始流，nowrap：无 zlib 头/尾，因整段随后被 AEAD 加密，完整性已由 GCM-SIV 保证）。
     *  级别取 L1（BEST_SPEED）：小块 JSON 的压缩率与 L6 几乎相同，大块也仅多 ~10 字节，但压缩更快。 */
    private static byte[] deflate(byte[] in) {
        java.util.zip.Deflater def = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_SPEED, true);
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

    /** inflate 解压（对应 deflate，nowrap 须与压缩端一致）。 */
    private static byte[] inflate(byte[] in) {
        java.util.zip.Inflater inf = new java.util.zip.Inflater(true);
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
}
