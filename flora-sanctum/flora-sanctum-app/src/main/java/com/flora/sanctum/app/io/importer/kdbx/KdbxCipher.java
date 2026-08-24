package com.flora.sanctum.app.io.importer.kdbx;

import javax.crypto.Cipher;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.UUID;

/**
 * KDBX4 载荷密码算法（走 JDK {@code javax.crypto}，不引入 BouncyCastle）。
 * <ul>
 *   <li>AES-256-CBC：PKCS#7 填充（JDK 用 PKCS5Padding 等价）。</li>
 *   <li>ChaCha20：12 字节 nonce、初始块计数器 0。</li>
 * </ul>
 */
final class KdbxCipher {

    /** AES-256 (CBC) 的 CipherID（取自 KeePass/KeePassXC 规范）。 */
    static final UUID AES = UUID.fromString("31c1f2e6-bf71-4350-be58-05216afc5aff");
    /** ChaCha20 的 CipherID（取自 KeePass/KeePassXC 规范）。 */
    static final UUID CHACHA20 = UUID.fromString("d6038a2b-8b6f-4cb5-a524-339a31dbb59a");

    private KdbxCipher() {
    }

    /** 解密载荷块。key 为 32 字节 finalKey，iv 为 EncryptionIV（AES 16 / ChaCha20 12）。 */
    static byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext, UUID cipherId) throws Exception {
        if (AES.equals(cipherId)) {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return c.doFinal(ciphertext);
        }
        if (CHACHA20.equals(cipherId)) {
            Cipher c = Cipher.getInstance("ChaCha20");
            byte[] nonce = Arrays.copyOf(iv, 12);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "ChaCha20"),
                    new ChaCha20ParameterSpec(nonce, 0));
            return c.doFinal(ciphertext);
        }
        throw new UnsupportedOperationException("不支持的 KDBX 加密算法: " + cipherId);
    }

    static boolean isSupported(UUID cipherId) {
        return AES.equals(cipherId) || CHACHA20.equals(cipherId);
    }
}
