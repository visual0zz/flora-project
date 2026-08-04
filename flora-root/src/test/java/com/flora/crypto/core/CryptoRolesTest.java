package com.flora.crypto.core;
import com.flora.crypto.core.combinator.BufferedAsymmetricBlockCipher;
import com.flora.crypto.core.combinator.PaddedAsymmetricBlockCipher;
import com.flora.crypto.core.combinator.PaddedBufferedBlockCipher;
import com.flora.crypto.core.interfaces.provider.AEADBlockCipher;
import com.flora.crypto.core.interfaces.provider.Agreement;
import com.flora.crypto.core.interfaces.provider.AsymmetricBlockCipher;
import com.flora.crypto.core.interfaces.provider.AsymmetricCipher;
import com.flora.crypto.core.interfaces.provider.AsymmetricCipherKeyPairGenerator;
import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.interfaces.Decapsulator;
import com.flora.crypto.core.interfaces.provider.DerivationFunction;
import com.flora.crypto.core.interfaces.Encapsulator;
import com.flora.crypto.core.interfaces.provider.EntropySource;
import com.flora.crypto.core.interfaces.provider.KEM;
import com.flora.crypto.core.interfaces.provider.Mac;
import com.flora.crypto.core.interfaces.provider.SP80090DRBG;
import com.flora.crypto.core.interfaces.SecretWithEncapsulation;
import com.flora.crypto.core.interfaces.provider.Xof;
import com.flora.crypto.core.keypair.AsymmetricCipherKeyPair;
import com.flora.crypto.core.keypair.AsymmetricKeyParameter;
import com.flora.crypto.core.param.HkdfParameters;
import com.flora.crypto.core.param.KdfParameters;
import com.flora.crypto.core.param.KeyGenerationParameters;
import com.flora.crypto.core.param.KeyParameter;
import com.flora.crypto.core.param.ParametersWithIV;
import com.flora.crypto.core.param.Pbkdf2Parameters;

import org.junit.jupiter.api.Test;

import com.flora.crypto.core.impl.HMacDrbg;
import com.flora.crypto.core.mode.CBCBlockCipher;
import com.flora.crypto.core.mode.GCMBlockCipher;
import com.flora.crypto.core.padding.PKCS1v15Padding;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class CryptoRolesTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    // ── ExtendedDigest：内部块长度 ──

    @Test
    void extendedDigestByteLength() {
        assertEquals(64, CryptoProvider.extendedDigest("SHA-256").getByteLength());
        assertEquals(128, CryptoProvider.extendedDigest("SHA-512").getByteLength());
    }

    // ── Xof：JDK 无能力，占位实现抛 UnsupportedOperationException ──

    @Test
    void xofPlaceholderThrows() {
        Xof xof = CryptoProvider.xof("SHAKE128");
        assertThrows(UnsupportedOperationException.class, () -> xof.doFinal(new byte[8], 0, 8));
        assertThrows(UnsupportedOperationException.class, () -> xof.doOutput(new byte[8], 0, 8));
    }

    // ── Agreement：ECDH 协商一致性 ──

    @Test
    void ecdhAgreementMatches() {
        AsymmetricCipherKeyPairGenerator genA = CryptoProvider.asymmetricKeyPairGenerator("EC");
        genA.init(new KeyGenerationParameters(RANDOM, 256));
        AsymmetricCipherKeyPair kpA = genA.generateKeyPair();
        AsymmetricCipherKeyPairGenerator genB = CryptoProvider.asymmetricKeyPairGenerator("EC");
        genB.init(new KeyGenerationParameters(RANDOM, 256));
        AsymmetricCipherKeyPair kpB = genB.generateKeyPair();

        Agreement a = CryptoProvider.agreement("ECDH");
        Agreement b = CryptoProvider.agreement("ECDH");
        a.init(kpA.getPrivate());
        b.init(kpB.getPrivate());

        byte[] sa = a.calculateAgreement(kpB.getPublic());
        byte[] sb = b.calculateAgreement(kpA.getPublic());
        assertArrayEquals(sa, sb);
    }

    // ── PBE / PBKDF2：自研实现与 JDK 直接计算结果一致 ──

    @Test
    void pbkdf2MatchesJdk() throws Exception {
        byte[] pw = "password".getBytes();
        byte[] salt = randomBytes(16);
        int iter = 1000;

        DerivationFunction pbkdf2 = CryptoProvider.derivationFunction("PBKDF2(HMac(SHA-256))");
        pbkdf2.init(new Pbkdf2Parameters(pw, salt, iter));
        byte[] derived = new byte[32];
        pbkdf2.generateBytes(derived, 0, 32);

        SecretKeyFactory sf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(new String(pw).toCharArray(), salt, iter, 256);
        byte[] expected = sf.generateSecret(spec).getEncoded();

        assertArrayEquals(expected, derived);
    }

    // ── DerivationFunction：KDF2 / HKDF 自洽 ──

    @Test
    void kdf2Deterministic() {
        byte[] secret = randomBytes(32);
        byte[] info = "ctx".getBytes();
        DerivationFunction f1 = CryptoProvider.derivationFunction("KDF2(SHA-256)");
        f1.init(new KdfParameters(secret, info));
        byte[] out1 = new byte[32];
        f1.generateBytes(out1, 0, 32);

        DerivationFunction f2 = CryptoProvider.derivationFunction("KDF2(SHA-256)");
        f2.init(new KdfParameters(secret, info));
        byte[] out2 = new byte[32];
        f2.generateBytes(out2, 0, 32);

        assertArrayEquals(out1, out2);
    }

    @Test
    void hkdfDeterministic() {
        byte[] prk = randomBytes(32);
        byte[] info = "ctx".getBytes();
        DerivationFunction f1 = CryptoProvider.derivationFunction("HKDF(HmacSHA256)");
        f1.init(new HkdfParameters(prk, info));
        byte[] out1 = new byte[32];
        f1.generateBytes(out1, 0, 32);

        DerivationFunction f2 = CryptoProvider.derivationFunction("HKDF(HmacSHA256)");
        f2.init(new HkdfParameters(prk, info));
        byte[] out2 = new byte[32];
        f2.generateBytes(out2, 0, 32);

        assertArrayEquals(out1, out2);
    }

    @Test
    void derivationFunctionPlaceholderThrows() {
        DerivationFunction f = CryptoProvider.derivationFunction("NONEXISTENT-KDF");
        assertThrows(UnsupportedOperationException.class, () -> f.generateBytes(new byte[8], 0, 8));
    }

    // ── AEADBlockCipher：自研 AES-GCM 往返（含 AAD 与标签）──

    @Test
    void aeadGcmRoundTrip() {
        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(12);
        byte[] aad = "header".getBytes();
        byte[] plain = "authenticated encryption".getBytes();

        AEADBlockCipher enc = new GCMBlockCipher(CryptoProvider.blockCipher("AES"));
        enc.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        enc.processAADBytes(aad, 0, aad.length);
        byte[] ctBuf = new byte[enc.getOutputSize(plain.length)];
        int n = enc.processBytes(plain, 0, plain.length, ctBuf, 0);
        int m = enc.doFinal(ctBuf, n);
        byte[] cipherWithTag = new byte[n + m];
        System.arraycopy(ctBuf, 0, cipherWithTag, 0, n + m);
        assertNotNull(enc.getMac());

        AEADBlockCipher dec = new GCMBlockCipher(CryptoProvider.blockCipher("AES"));
        dec.init(false, new ParametersWithIV(new KeyParameter(key), iv));
        dec.processAADBytes(aad, 0, aad.length);
        byte[] ptBuf = new byte[dec.getOutputSize(cipherWithTag.length)];
        int p = dec.processBytes(cipherWithTag, 0, cipherWithTag.length, ptBuf, 0);
        int q = dec.doFinal(ptBuf, p);
        byte[] recovered = new byte[p + q];
        System.arraycopy(ptBuf, 0, recovered, 0, p + q);

        assertArrayEquals(plain, recovered);
    }

    // ── GCM：篡改标签应校验失败 ──

    @Test
    void gcmTamperedTagRejected() {
        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(12);
        byte[] plain = "tamper test".getBytes();

        GCMBlockCipher enc = new GCMBlockCipher(CryptoProvider.blockCipher("AES"));
        enc.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] ct = new byte[enc.getOutputSize(plain.length)];
        int len = enc.processBytes(plain, 0, plain.length, ct, 0);
        len += enc.doFinal(ct, len);

        ct[0] ^= 0xFF; // 篡改密文

        GCMBlockCipher dec = new GCMBlockCipher(CryptoProvider.blockCipher("AES"));
        dec.init(false, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] out = new byte[dec.getOutputSize(ct.length)];
        dec.processBytes(ct, 0, ct.length, out, 0);
        assertThrows(IllegalStateException.class, () -> dec.doFinal(out, 0));
    }

    // ── 模式对象：CBC 链式（纯 Java）往返 ──

    @Test
    void cbcModeRoundTrip() {
        BlockCipher raw = CryptoProvider.blockCipher("AES");
        BlockCipher cbc = new CBCBlockCipher(raw);

        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(16);
        byte[] plain = randomBytes(32); // 块对齐

        cbc.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] ct = cbc.process(plain);

        cbc.init(false, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] pt = cbc.process(ct);

        assertArrayEquals(plain, pt);
    }

    // ── 模式对象 + 填充：PaddedBufferedBlockCipher(PKCS7) 非对齐往返 ──

    @Test
    void paddedBufferedPkc7RoundTrip() {
        BlockCipher raw = CryptoProvider.blockCipher("AES");
        PaddedBufferedBlockCipher p = new PaddedBufferedBlockCipher(raw, new com.flora.crypto.core.padding.PKCS7Padding());

        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(16);
        byte[] plain = randomBytes(20); // 非块对齐

        p.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] ct = p.process(plain);

        p.init(false, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] pt = p.process(ct);

        assertArrayEquals(plain, pt);
    }

    // ── 非对称流式：BufferedAsymmetricBlockCipher 包裹「裸 RSA + PKCS1v1.5」往返 ──

    @Test
    void asymmetricStreamCipherRoundTrip() {
        KeyPair kp = CryptoProvider.keyPairGenerator("RSA").generate(2048);
        AsymmetricBlockCipher encBase = new PaddedAsymmetricBlockCipher(
                CryptoProvider.asymmetricCipher("RSA"), new PKCS1v15Padding());
        encBase.init(true, new AsymmetricKeyParameter(kp.getPublic()));
        int inSize = encBase.getInputBlockSize();
        byte[] data = randomBytes(inSize);

        AsymmetricCipher enc = new BufferedAsymmetricBlockCipher(encBase);
        enc.init(true, new AsymmetricKeyParameter(kp.getPublic()));
        byte[] buf = new byte[1024];
        int n = enc.processBytes(data, 0, data.length, buf, 0);

        AsymmetricBlockCipher decBase = new PaddedAsymmetricBlockCipher(
                CryptoProvider.asymmetricCipher("RSA"), new PKCS1v15Padding());
        AsymmetricCipher dec = new BufferedAsymmetricBlockCipher(decBase);
        dec.init(false, new AsymmetricKeyParameter(kp.getPrivate()));
        byte[] out = new byte[1024];
        int m = dec.processBytes(buf, 0, n, out, 0);
        byte[] recovered = new byte[m];
        System.arraycopy(out, 0, recovered, 0, m);

        assertArrayEquals(data, recovered);
    }

    // ── KEM：ECDH 封装/解封装得到同一对称密钥 ──

    @Test
    void kemEcdhRoundTrip() {
        AsymmetricCipherKeyPairGenerator gen = CryptoProvider.asymmetricKeyPairGenerator("EC");
        gen.init(new KeyGenerationParameters(RANDOM, 256));
        AsymmetricCipherKeyPair kp = gen.generateKeyPair();

        KEM kem = CryptoProvider.kem("ECDH");
        Encapsulator enc = kem.newEncapsulator(kp.getPublic());
        SecretWithEncapsulation swc = enc.encapsulate();
        assertEquals(32, enc.getSecretLength());
        assertEquals(enc.getEncapsulationLength(), swc.getEncapsulation().length);

        Decapsulator dec = kem.newDecapsulator(kp.getPrivate());
        SecretWithEncapsulation swc2 = dec.decapsulate(swc.getEncapsulation());

        assertArrayEquals(swc.getSecret(), swc2.getSecret());
        assertNotEquals(0, swc.getSecret().length);
    }

    // ── KEM：X25519 封装/解封装往返 ──

    @Test
    void kemX25519RoundTrip() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
        KeyPair kp = kpg.generateKeyPair();

        KEM kem = CryptoProvider.kem("X25519");
        Encapsulator enc = kem.newEncapsulator(new AsymmetricKeyParameter(kp.getPublic()));
        SecretWithEncapsulation swc = enc.encapsulate();

        Decapsulator dec = kem.newDecapsulator(new AsymmetricKeyParameter(kp.getPrivate()));
        SecretWithEncapsulation swc2 = dec.decapsulate(swc.getEncapsulation());

        assertArrayEquals(swc.getSecret(), swc2.getSecret());
    }

    // ── KEM：destroy 清除密钥材料 ──

    @Test
    void kemSecretDestroyZeroes() {
        AsymmetricCipherKeyPairGenerator gen = CryptoProvider.asymmetricKeyPairGenerator("EC");
        gen.init(new KeyGenerationParameters(RANDOM, 256));
        AsymmetricCipherKeyPair kp = gen.generateKeyPair();

        KEM kem = CryptoProvider.kem("ECDH");
        SecretWithEncapsulation swc = kem.newEncapsulator(kp.getPublic()).encapsulate();
        byte[] secretBefore = swc.getSecret().clone();
        assertNotEquals(0, secretBefore.length);

        swc.destroy();
        assertArrayEquals(new byte[0], swc.getSecret());
    }

    // ── KEM：未知算法走占位实现，抛 UnsupportedOperationException ──

    @Test
    void kemPlaceholderThrows() {
        KEM kem = CryptoProvider.kem("NONEXISTENT-KEM");
        assertThrows(UnsupportedOperationException.class,
                () -> kem.newEncapsulator(new AsymmetricKeyParameter(
                        CryptoProvider.keyPairGenerator("RSA").generate(2048).getPublic())));
    }

    // ── EntropySource：默认实现取熵长度正确 ──

    @Test
    void entropySourceLength() {
        EntropySource es = CryptoProvider.entropySource();
        assertTrue(es.isPredictionResistant());
        byte[] entropy = es.getEntropy(256);
        assertEquals(32, entropy.length); // ceil(256/8)
    }

    // ── SP800-90A HMAC_DRBG：确定性（同种子同输出）──

    @Test
    void hmacDrbgDeterministic() {
        byte[] entropy = randomBytes(32);
        byte[] nonce = randomBytes(16);
        Mac mac1 = CryptoProvider.mac("HmacSHA256");
        Mac mac2 = CryptoProvider.mac("HmacSHA256");
        HMacDrbg d1 = new HMacDrbg(mac1, entropy, nonce, null);
        HMacDrbg d2 = new HMacDrbg(mac2, entropy, nonce, null);

        byte[] out1 = new byte[64];
        byte[] out2 = new byte[64];
        assertEquals(64 * 8, d1.generate(out1, null, false));
        assertEquals(64 * 8, d2.generate(out2, null, false));
        assertArrayEquals(out1, out2);

        // 不同种子 → 不同输出
        HMacDrbg d3 = new HMacDrbg(CryptoProvider.mac("HmacSHA256"), randomBytes(32), nonce, null);
        byte[] out3 = new byte[64];
        d3.generate(out3, null, false);
        assertFalse(java.util.Arrays.equals(out1, out3));
    }

    // ── HMAC_DRBG：个性化字符串影响输出 ──

    @Test
    void hmacDrbgPersonalizationChangesOutput() {
        byte[] entropy = randomBytes(32);
        byte[] nonce = randomBytes(16);
        HMacDrbg a = new HMacDrbg(CryptoProvider.mac("HmacSHA256"), entropy, nonce, "p1".getBytes());
        HMacDrbg b = new HMacDrbg(CryptoProvider.mac("HmacSHA256"), entropy, nonce, "p2".getBytes());
        byte[] oa = new byte[64];
        byte[] ob = new byte[64];
        a.generate(oa, null, false);
        b.generate(ob, null, false);
        assertFalse(java.util.Arrays.equals(oa, ob));
    }

    // ── HMAC_DRBG：CryptoProvider 工厂（实时熵源）可生成 ──

    @Test
    void hmacDrbgFactoryGenerates() {
        SP80090DRBG drbg = CryptoProvider.hmacDrbg("HmacSHA256", 256, null);
        assertEquals(32, drbg.getBlockSize());
        byte[] out = new byte[48];
        assertTrue(drbg.generate(out, null, false) > 0);
    }
}
