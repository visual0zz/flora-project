package com.flora.crypto.core;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.KeyPair;
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

    // ── Wrapper：AES 密钥包装往返 ──

    @Test
    void aesKeyWrapRoundTrip() {
        Wrapper w = CryptoProvider.wrapper("AESWrap");
        byte[] wrapKey = randomBytes(16);
        byte[] toWrap = randomBytes(16);

        w.init(true, new KeyParameter(wrapKey));
        byte[] wrapped = w.wrap(toWrap, 0, toWrap.length);

        w.init(false, new KeyParameter(wrapKey));
        byte[] unwrapped = w.unwrap(wrapped, 0, wrapped.length);

        assertArrayEquals(toWrap, unwrapped);
        assertNotEquals(0, wrapped.length);
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

    // ── PBE / PBKDF2：与 JDK 直接计算结果一致 ──

    @Test
    void pbkdf2MatchesJdk() throws Exception {
        byte[] pw = "password".getBytes();
        byte[] salt = randomBytes(16);
        int iter = 1000;

        PBEParametersGenerator pbe = CryptoProvider.pbeParametersGenerator("PBKDF2WithHmacSHA256");
        pbe.init(pw, salt, iter);
        byte[] derived = ((KeyParameter) pbe.generateDerivedParameters(256)).getKey();

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
        DerivationFunction f1 = CryptoProvider.derivationFunction("KDF2");
        f1.init(new KdfParameters(secret, info));
        byte[] out1 = new byte[32];
        f1.generateBytes(out1, 0, 32);

        DerivationFunction f2 = CryptoProvider.derivationFunction("KDF2");
        f2.init(new KdfParameters(secret, info));
        byte[] out2 = new byte[32];
        f2.generateBytes(out2, 0, 32);

        assertArrayEquals(out1, out2);
    }

    @Test
    void hkdfDeterministic() {
        byte[] prk = randomBytes(32);
        byte[] info = "ctx".getBytes();
        DerivationFunction f1 = CryptoProvider.derivationFunction("HKDF");
        f1.init(new HkdfParameters(prk, info));
        byte[] out1 = new byte[32];
        f1.generateBytes(out1, 0, 32);

        DerivationFunction f2 = CryptoProvider.derivationFunction("HKDF");
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

    // ── AEADBlockCipher：AES-GCM 往返（含 AAD 与标签）──

    @Test
    void aeadGcmRoundTrip() {
        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(12);
        byte[] aad = "header".getBytes();
        byte[] plain = "authenticated encryption".getBytes();

        AEADBlockCipher enc = CryptoProvider.aeadBlockCipher("AES/GCM/NoPadding");
        enc.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        enc.processAADBytes(aad, 0, aad.length);
        byte[] ctBuf = new byte[enc.getOutputSize(plain.length)];
        int n = enc.processBytes(plain, 0, plain.length, ctBuf, 0);
        int m = enc.doFinal(ctBuf, n);
        byte[] cipherWithTag = new byte[n + m];
        System.arraycopy(ctBuf, 0, cipherWithTag, 0, n + m);
        assertNotNull(enc.getMac());

        AEADBlockCipher dec = CryptoProvider.aeadBlockCipher("AES/GCM/NoPadding");
        dec.init(false, new ParametersWithIV(new KeyParameter(key), iv));
        dec.processAADBytes(aad, 0, aad.length);
        byte[] ptBuf = new byte[dec.getOutputSize(cipherWithTag.length)];
        int p = dec.processBytes(cipherWithTag, 0, cipherWithTag.length, ptBuf, 0);
        int q = dec.doFinal(ptBuf, p);
        byte[] recovered = new byte[p + q];
        System.arraycopy(ptBuf, 0, recovered, 0, p + q);

        assertArrayEquals(plain, recovered);
    }

    // ── 模式对象：CBC 链式（纯 Java）往返 ──

    @Test
    void cbcModeRoundTrip() {
        BlockCipher raw = CryptoProvider.blockCipher("AES/ECB/NoPadding");
        BlockCipher cbc = new com.flora.crypto.core.mode.CBCBlockCipher(raw);

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
        BlockCipher raw = CryptoProvider.blockCipher("AES/ECB/NoPadding");
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

    // ── 非对称流式：BufferedAsymmetricBlockCipher 包裹 RSA 往返 ──

    @Test
    void asymmetricStreamCipherRoundTrip() {
        KeyPair kp = CryptoProvider.keyPairGenerator("RSA").generate(2048);
        AsymmetricBlockCipher base = CryptoProvider.asymmetricCipher("RSA/ECB/PKCS1Padding");
        base.init(true, new AsymmetricKeyParameter(kp.getPublic()));
        int inSize = base.getInputBlockSize();
        byte[] data = randomBytes(inSize);

        AsymmetricCipher enc = CryptoProvider.asymmetricStreamCipher("RSA/ECB/PKCS1Padding");
        enc.init(true, new AsymmetricKeyParameter(kp.getPublic()));
        byte[] buf = new byte[1024];
        int n = enc.processBytes(data, 0, data.length, buf, 0);

        AsymmetricCipher dec = CryptoProvider.asymmetricStreamCipher("RSA/ECB/PKCS1Padding");
        dec.init(false, new AsymmetricKeyParameter(kp.getPrivate()));
        byte[] out = new byte[1024];
        int m = dec.processBytes(buf, 0, n, out, 0);
        byte[] recovered = new byte[m];
        System.arraycopy(out, 0, recovered, 0, m);

        assertArrayEquals(data, recovered);
    }
}
