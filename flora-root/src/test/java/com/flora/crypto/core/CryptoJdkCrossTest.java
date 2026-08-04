package com.flora.crypto.core;
import com.flora.crypto.core.combinator.PaddedAsymmetricBlockCipher;
import com.flora.crypto.core.interfaces.provider.AsymmetricBlockCipher;
import com.flora.crypto.core.keypair.AsymmetricKeyParameter;
import com.flora.crypto.core.param.KeyParameter;
import com.flora.crypto.core.param.ParametersWithIV;

import com.flora.crypto.core.mode.GCMBlockCipher;
import com.flora.crypto.core.padding.OAEPPadding;
import com.flora.crypto.core.padding.PKCS1v15Padding;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.KeyPair;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自研组合层与 JDK 的交叉验证测试。
 * <p>每个自研算法都做双向交叉验证：自研加密 → JDK 解密，JDK 加密 → 自研解密，
 * 确保自研实现与 JDK 标准实现在字节层面互操作。</p>
 */
class CryptoJdkCrossTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    // ── GCM：自研加密 → JDK 解密 ──

    @Test
    void gcmFloraEncryptJdkDecrypt() throws Exception {
        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(12);
        byte[] plain = randomBytes(40); // 非块对齐

        // 自研 GCM 加密
        GCMBlockCipher enc = new GCMBlockCipher(CryptoProvider.blockCipher("AES"));
        enc.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] ct = new byte[enc.getOutputSize(plain.length)];
        int len = enc.processBytes(plain, 0, plain.length, ct, 0);
        len += enc.doFinal(ct, len);
        byte[] cipherWithTag = java.util.Arrays.copyOf(ct, len);

        // JDK GCM 解密
        Cipher jdk = Cipher.getInstance("AES/GCM/NoPadding");
        jdk.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] recovered = jdk.doFinal(cipherWithTag);

        assertArrayEquals(plain, recovered);
    }

    // ── GCM：JDK 加密 → 自研解密 ──

    @Test
    void gcmJdkEncryptFloraDecrypt() throws Exception {
        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(12);
        byte[] plain = randomBytes(63); // 非块对齐

        // JDK GCM 加密
        Cipher jdk = Cipher.getInstance("AES/GCM/NoPadding");
        jdk.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] cipherWithTag = jdk.doFinal(plain);

        // 自研 GCM 解密
        GCMBlockCipher dec = new GCMBlockCipher(CryptoProvider.blockCipher("AES"));
        dec.init(false, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] pt = new byte[dec.getOutputSize(cipherWithTag.length)];
        int len = dec.processBytes(cipherWithTag, 0, cipherWithTag.length, pt, 0);
        len += dec.doFinal(pt, len);

        assertArrayEquals(plain, java.util.Arrays.copyOf(pt, len));
    }

    // ── GCM：自研加密（含 AAD）→ JDK 解密 ──

    @Test
    void gcmAadCrossValidate() throws Exception {
        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(12);
        byte[] aad = "header-metadata".getBytes();
        byte[] plain = randomBytes(48);

        // 自研加密（含 AAD）
        GCMBlockCipher enc = new GCMBlockCipher(CryptoProvider.blockCipher("AES"));
        enc.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        enc.processAADBytes(aad, 0, aad.length);
        byte[] ct = new byte[enc.getOutputSize(plain.length)];
        int len = enc.processBytes(plain, 0, plain.length, ct, 0);
        len += enc.doFinal(ct, len);
        byte[] cipherWithTag = java.util.Arrays.copyOf(ct, len);

        // JDK 解密（含 AAD）
        Cipher jdk = Cipher.getInstance("AES/GCM/NoPadding");
        jdk.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        jdk.updateAAD(aad);
        byte[] recovered = jdk.doFinal(cipherWithTag);

        assertArrayEquals(plain, recovered);
    }

    // ── GCM：JDK 加密（含 AAD）→ 自研解密 ──

    @Test
    void gcmAadJdkEncryptFloraDecrypt() throws Exception {
        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(12);
        byte[] aad = "header-metadata".getBytes();
        byte[] plain = randomBytes(33);

        Cipher jdk = Cipher.getInstance("AES/GCM/NoPadding");
        jdk.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        jdk.updateAAD(aad);
        byte[] cipherWithTag = jdk.doFinal(plain);

        GCMBlockCipher dec = new GCMBlockCipher(CryptoProvider.blockCipher("AES"));
        dec.init(false, new ParametersWithIV(new KeyParameter(key), iv));
        dec.processAADBytes(aad, 0, aad.length);
        byte[] pt = new byte[dec.getOutputSize(cipherWithTag.length)];
        int len = dec.processBytes(cipherWithTag, 0, cipherWithTag.length, pt, 0);
        len += dec.doFinal(pt, len);

        assertArrayEquals(plain, java.util.Arrays.copyOf(pt, len));
    }

    // ── GCM：非 96 位 IV 交叉验证 ──

    @Test
    void gcmNonStandardIvCrossValidate() throws Exception {
        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(16); // 非 96 位 → GHASH 派生 J0
        byte[] plain = randomBytes(20);

        GCMBlockCipher enc = new GCMBlockCipher(CryptoProvider.blockCipher("AES"));
        enc.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] ct = new byte[enc.getOutputSize(plain.length)];
        int len = enc.processBytes(plain, 0, plain.length, ct, 0);
        len += enc.doFinal(ct, len);
        byte[] cipherWithTag = java.util.Arrays.copyOf(ct, len);

        Cipher jdk = Cipher.getInstance("AES/GCM/NoPadding");
        jdk.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] recovered = jdk.doFinal(cipherWithTag);

        assertArrayEquals(plain, recovered);
    }

    // ── PKCS1v1.5：自研填充加密 → JDK 解密 ──

    @Test
    void pkcs1FloraEncryptJdkDecrypt() throws Exception {
        KeyPair kp = CryptoProvider.keyPairGenerator("RSA").generate(2048);
        byte[] plain = randomBytes(20);

        AsymmetricBlockCipher enc = new PaddedAsymmetricBlockCipher(
                CryptoProvider.asymmetricCipher("RSA"), new PKCS1v15Padding());
        enc.init(true, new AsymmetricKeyParameter(kp.getPublic()));
        byte[] cipherText = enc.processBlock(plain, 0, plain.length);

        Cipher jdk = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        jdk.init(Cipher.DECRYPT_MODE, kp.getPrivate());
        byte[] recovered = jdk.doFinal(cipherText);

        assertArrayEquals(plain, recovered);
    }

    // ── PKCS1v1.5：JDK 加密 → 自研填充解密 ──

    @Test
    void pkcs1JdkEncryptFloraDecrypt() throws Exception {
        KeyPair kp = CryptoProvider.keyPairGenerator("RSA").generate(2048);
        byte[] plain = randomBytes(20);

        Cipher jdk = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        jdk.init(Cipher.ENCRYPT_MODE, kp.getPublic());
        byte[] cipherText = jdk.doFinal(plain);

        AsymmetricBlockCipher dec = new PaddedAsymmetricBlockCipher(
                CryptoProvider.asymmetricCipher("RSA"), new PKCS1v15Padding());
        dec.init(false, new AsymmetricKeyParameter(kp.getPrivate()));
        byte[] recovered = dec.processBlock(cipherText, 0, cipherText.length);

        assertArrayEquals(plain, recovered);
    }

    // ── OAEP(SHA-256)：自研填充加密 → JDK 解密 ──

    @Test
    void oaepFloraEncryptJdkDecrypt() throws Exception {
        KeyPair kp = CryptoProvider.keyPairGenerator("RSA").generate(2048);
        byte[] plain = randomBytes(20);

        AsymmetricBlockCipher enc = new PaddedAsymmetricBlockCipher(
                CryptoProvider.asymmetricCipher("RSA"),
                new OAEPPadding(CryptoProvider.digest("SHA-256")));
        enc.init(true, new AsymmetricKeyParameter(kp.getPublic()));
        byte[] cipherText = enc.processBlock(plain, 0, plain.length);

        // JDK 的 OAEPWithSHA-256AndMGF1Padding 默认 MGF1 用 SHA-1，需显式指定 MGF1=SHA-256
        Cipher jdk = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec spec = new OAEPParameterSpec("SHA-256", "MGF1",
                new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
        jdk.init(Cipher.DECRYPT_MODE, kp.getPrivate(), spec);
        byte[] recovered = jdk.doFinal(cipherText);

        assertArrayEquals(plain, recovered);
    }

    // ── OAEP(SHA-256)：JDK 加密 → 自研填充解密 ──

    @Test
    void oaepJdkEncryptFloraDecrypt() throws Exception {
        KeyPair kp = CryptoProvider.keyPairGenerator("RSA").generate(2048);
        byte[] plain = randomBytes(30);

        Cipher jdk = Cipher.getInstance("RSA/ECB/OAEPPadding");
        OAEPParameterSpec spec = new OAEPParameterSpec("SHA-256", "MGF1",
                new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT);
        jdk.init(Cipher.ENCRYPT_MODE, kp.getPublic(), spec);
        byte[] cipherText = jdk.doFinal(plain);

        AsymmetricBlockCipher dec = new PaddedAsymmetricBlockCipher(
                CryptoProvider.asymmetricCipher("RSA"),
                new OAEPPadding(CryptoProvider.digest("SHA-256")));
        dec.init(false, new AsymmetricKeyParameter(kp.getPrivate()));
        byte[] recovered = dec.processBlock(cipherText, 0, cipherText.length);

        assertArrayEquals(plain, recovered);
    }
}
