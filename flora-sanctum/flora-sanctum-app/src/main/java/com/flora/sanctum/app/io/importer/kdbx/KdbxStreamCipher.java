package com.flora.sanctum.app.io.importer.kdbx;

import javax.crypto.Cipher;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * KDBX 内层随机流：对 {@code Protected="True"} 字段的顺序密钥流（不随每个字段重置）。
 * <p>内层随机流算法 ID（取自 KeePass/KeePassXC 规范）：2=Salsa20，3=ChaCha20。</p>
 * <p>密钥派生（按 KeePass 实现，见 KeePassLib {@code Crypto/ProtectedStream} 与
 * KeePassXC {@code format/KeePass2}）：内层头写入的是原始随机种子，并不直接作为密钥。</p>
 * <ul>
 *   <li>Salsa20：密钥 = SHA-256(种子) 的低 32 字节，固定 8 字节 IV
 *       {@code e8 30 09 4b 97 20 5d 2a}。</li>
 *   <li>ChaCha20：先对种子做 SHA-512，低 32 字节为密钥、随后 12 字节为 nonce。</li>
 * </ul>
 */
final class KdbxStreamCipher {

    /** KeePass 内层 Salsa20 固定 IV。 */
    private static final byte[] SALSA20_IV = {
            (byte) 0xe8, 0x30, 0x09, 0x4b, (byte) 0x97, 0x20, 0x5d, (byte) 0x2a
    };

    private final int type;
    private final byte[] key;
    private final byte[] nonce;
    private final Salsa20 salsa;
    private long position;

    KdbxStreamCipher(int innerStreamId, byte[] innerKey) {
        // KeePass 内层随机流密钥由内层头写入的原始种子派生（见类注释），
        // 种子长度可能为 32 或 64 字节，统一按规范做散列派生。
        byte[] seed = innerKey == null ? new byte[32] : innerKey;
        if (innerStreamId == 2) {
            this.type = 2;
            this.key = sha256(seed);
            this.nonce = SALSA20_IV;
            this.salsa = new Salsa20(this.key, SALSA20_IV);
        } else if (innerStreamId == 3) {
            this.type = 3;
            byte[] kh = sha512(seed);
            this.key = new byte[32];
            this.nonce = new byte[12];
            System.arraycopy(kh, 0, this.key, 0, 32);
            System.arraycopy(kh, 32, this.nonce, 0, 12);
            this.salsa = null;
        } else {
            throw new IllegalArgumentException("不支持的内层随机流 id=" + innerStreamId);
        }
    }

    /** 解密受保护字段值（Base64 密文 → 明文）。 */
    String decrypt(String base64Value) {
        byte[] cipher;
        try {
            cipher = Base64.getDecoder().decode(base64Value);
        } catch (IllegalArgumentException e) {
            return base64Value; // 非 Base64 则原样返回
        }
        byte[] ks = keystream(cipher.length);
        byte[] plain = new byte[cipher.length];
        for (int i = 0; i < cipher.length; i++) {
            plain[i] = (byte) (cipher[i] ^ ks[i]);
        }
        position += cipher.length;
        return new String(plain, StandardCharsets.UTF_8);
    }

    private byte[] keystream(int len) {
        if (type == 2) {
            return salsa.keystream(len, position);
        }
        // ChaCha20：用 JDK 逐 64 字节块生成密钥流（连续推进 position）
        byte[] out = new byte[len];
        int produced = 0;
        long pos = position;
        try {
            while (produced < len) {
                int block = (int) (pos / 64);
                int off = (int) (pos % 64);
                Cipher c = Cipher.getInstance("ChaCha20");
                c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"),
                        new ChaCha20ParameterSpec(nonce, block));
                byte[] blk = c.doFinal(new byte[64]);
                int take = Math.min(64 - off, len - produced);
                System.arraycopy(blk, off, out, produced, take);
                produced += take;
                pos += (64 - off);
            }
        } catch (Exception e) {
            throw new IllegalStateException("ChaCha20 内层流失败", e);
        }
        return out;
    }

    private static byte[] sha256(byte[] d) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(d);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static byte[] sha512(byte[] d) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(d);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-512 不可用", e);
        }
    }
}
