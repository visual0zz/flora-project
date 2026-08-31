package com.flora.root.crypto;

import com.flora.root.tag.SlowFunction;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * KeePass 系 AES-KDF（KDBX2/3/4 与 KeePass1 共用）的密钥派生封装。
 * <p>变换过程：以 {@code seed} 为 AES-256-ECB 密钥，对 32 字节输入整体（两个 16 字节块）加密
 * {@code rounds} 轮，结果再取 SHA-256；最终密钥为 {@code SHA-256(masterSeed ‖ transformed)}。</p>
 * <p>仅暴露这两个步骤，供 KDBX / KeePass1 等外部格式在已知盐与轮数下派生对称密钥，
 * 不涉及任何具体文件格式或上层口令逻辑。</p>
 */
public final class AesKdf {

    private AesKdf() {
    }

    /**
     * AES-KDF 变换：{@code SHA-256( AES-256-ECB<sub>seed</sub><sup>rounds</sup>(input) )}。
     *
     * @param input  32 字节输入（复合主密钥）
     * @param seed   32 字节种子（KDBX 的 TransformSeed / KeePass1 的 TransformSeed）
     * @param rounds 变换轮数（KDBX 的 TransformRounds）
     * @return 32 字节变换结果
     * @throws IllegalArgumentException 输入/种子长度不合法，或轮数为非正数
     */
    @SlowFunction(seconds = 1)
    public static byte[] transform(byte[] input, byte[] seed, long rounds) {
        if (input == null || input.length != 32) {
            throw new IllegalArgumentException("AES-KDF 输入必须为 32 字节");
        }
        if (seed == null || seed.length != 32) {
            throw new IllegalArgumentException("AES-KDF 种子必须为 32 字节");
        }
        if (rounds <= 0) {
            throw new IllegalArgumentException("AES-KDF 轮数必须为正数: " + rounds);
        }
        try {
            Cipher c = Cipher.getInstance("AES/ECB/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(seed, "AES"));
            byte[] transformed = input.clone();
            for (long i = 0; i < rounds; i++) {
                transformed = c.doFinal(transformed);
            }
            return sha256(transformed);
        } catch (NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException
                 | java.security.InvalidKeyException | javax.crypto.IllegalBlockSizeException
                 | javax.crypto.BadPaddingException e) {
            throw new IllegalStateException("AES-KDF 计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * 由变换结果与主种子合成最终对称密钥：{@code SHA-256(masterSeed ‖ transformed)}。
     *
     * @param masterSeed 16 字节主种子（KDBX 的 MasterSeed / KeePass1 的 MasterSeed）
     * @param transformed {@link #transform} 的输出
     * @return 32 字节对称密钥
     */
    public static byte[] finalKey(byte[] masterSeed, byte[] transformed) {
        if (masterSeed == null || masterSeed.length == 0) {
            throw new IllegalArgumentException("缺少 MasterSeed");
        }
        if (transformed == null || transformed.length != 32) {
            throw new IllegalArgumentException("变换结果必须为 32 字节");
        }
        byte[] buf = new byte[masterSeed.length + 32];
        System.arraycopy(masterSeed, 0, buf, 0, masterSeed.length);
        System.arraycopy(transformed, 0, buf, masterSeed.length, 32);
        return sha256(buf);
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("缺少 SHA-256 实现", e);
        }
    }
}
