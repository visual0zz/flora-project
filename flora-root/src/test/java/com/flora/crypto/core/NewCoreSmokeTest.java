package com.flora.crypto.core;

import com.flora.common.register.AlgorithmFactory;
import com.flora.crypto.core.wrapper.PaddedBufferedBlockCipherWrapper;
import com.flora.crypto.core.interfaces.algorithm.BlockCipher;
import com.flora.crypto.core.interfaces.material.param.CipherParameter;
import com.flora.crypto.core.link.CBCBlockCipher;
import com.flora.crypto.core.link.SICBlockCipher;
import com.flora.crypto.core.padding.PKCS7Padding;
import com.flora.crypto.core.padding.ZeroBytePadding;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * newcore 骨架冒烟测试：验证 DSL 注册中心、模式与填充组合器可真实运行。
 * <p>由于纯算法与 JDK 桥接尚未搬运，测试使用一个简单的 XOR 伪分组密码作为引擎，
 * 只验证 newcore 的注册 / 组合 / 模式链路本身。</p>
 */
class NewCoreSmokeTest {

    /** XOR 伪分组密码（16 字节块），仅用于验证模式与组合器链路。 */
    static final class XorBlockCipher implements BlockCipher {

        private byte[] key = new byte[16];
        private boolean forEncryption;

        XorBlockCipher() {
        }

        XorBlockCipher(byte[] key) {
            this.key = key.clone();
        }

        @Override
        public void init(boolean forEncryption, CipherParameter params) {
            this.forEncryption = forEncryption;
        }

        @Override
        public String getAlgorithmName() {
            return "XOR";
        }

        @Override
        public int getBlockSize() {
            return 16;
        }

        @Override
        public int processBlock(byte[] in, int inOff, byte[] out, int outOff) {
            for (int i = 0; i < 16; i++) {
                out[outOff + i] = (byte) (in[inOff + i] ^ key[i % key.length]);
            }
            return 16;
        }

        @Override
        public AlgorithmFactory<? extends BlockCipher> factory() {
            throw new UnsupportedOperationException("测试桩不参与注册");
        }
    }

    @Test
    void dslRegistrationAndResolution() {
        // CryptoProvider 静态初始化时已注册内置算法族（含填充），此处直接按 DSL 解析
        Object pkcs7 = CryptoProvider.resolve("PKCS7");
        assertInstanceOf(PKCS7Padding.class, pkcs7);
        assertEquals("PKCS7", ((PKCS7Padding) pkcs7).getAlgorithmName());

        Object zero = CryptoProvider.resolve("ZeroByte");
        assertInstanceOf(ZeroBytePadding.class, zero);

        // JDK 裸原语桥接：AES / SHA-256 / HmacSHA256 现可通过 DSL 解析
        assertInstanceOf(com.flora.crypto.core.bridge.JdkBlockCipher.class,
                CryptoProvider.resolve("AES"));
        assertInstanceOf(com.flora.crypto.core.bridge.JdkDigest.class,
                CryptoProvider.resolve("SHA-256"));
        assertInstanceOf(com.flora.crypto.core.bridge.JdkMac.class,
                CryptoProvider.resolve("HmacSHA256"));

        // 纯 Java 摘要 / MAC 原语
        assertInstanceOf(com.flora.crypto.core.impl.Blake2bDigest.class,
                CryptoProvider.resolve("BLAKE2B-256"));
        assertInstanceOf(com.flora.crypto.core.impl.Ripemd160Digest.class,
                CryptoProvider.resolve("RIPEMD160"));
        assertInstanceOf(com.flora.crypto.core.impl.Poly1305Mac.class,
                CryptoProvider.resolve("Poly1305"));
        assertInstanceOf(com.flora.crypto.core.impl.HMac.class,
                CryptoProvider.resolve("HMac(SHA-256)"));

        // 密钥派生 / 口令哈希
        assertInstanceOf(com.flora.crypto.core.impl.Pbkdf2DerivationFunction.class,
                CryptoProvider.resolve("PBKDF2(HMac(SHA-256))"));
        assertInstanceOf(com.flora.crypto.core.impl.Kdf2DerivationFunction.class,
                CryptoProvider.resolve("KDF2(SHA-256)"));
        assertInstanceOf(com.flora.crypto.core.impl.HkdfDerivationFunction.class,
                CryptoProvider.resolve("HKDF(HMac(SHA-256))"));
        assertInstanceOf(com.flora.crypto.core.impl.Scrypt.class,
                CryptoProvider.resolve("scrypt"));
        assertInstanceOf(com.flora.crypto.core.impl.BCrypt.class,
                CryptoProvider.resolve("bcrypt"));
        assertInstanceOf(com.flora.crypto.core.impl.Argon2.class,
                CryptoProvider.resolve("Argon2id"));
        // AEAD 流式密码
        assertInstanceOf(com.flora.crypto.core.impl.ChaCha20Poly1305.class,
                CryptoProvider.resolve("ChaCha20Poly1305"));
        // JDK 非对称桥接（Agreement / 非对称分组密码 / 密钥对生成 / KEM / 签名 / 熵源）
        assertInstanceOf(com.flora.crypto.core.bridge.JdkAgreement.class,
                CryptoProvider.resolve("ECDH"));
        assertInstanceOf(com.flora.crypto.core.bridge.JdkAsymmetricBlockCipher.class,
                CryptoProvider.resolve("RSA"));
        assertInstanceOf(com.flora.crypto.core.bridge.JdkAsymmetricKeyPairGenerator.class,
                CryptoProvider.resolve("EC"));
        assertInstanceOf(com.flora.crypto.core.bridge.JdkSignature.class,
                CryptoProvider.resolve("SHA256withRSA"));
        assertInstanceOf(com.flora.crypto.core.bridge.SecureRandomEntropySource.class,
                CryptoProvider.resolve("SecureRandom"));
        // JDK KEM 桥接（ML-KEM / Kyber 等 JDK 原生 KEM）
        assertInstanceOf(com.flora.crypto.core.bridge.JdkKem.class,
                CryptoProvider.resolve("ML-KEM"));
        // 基于密钥协商的 KEM（与裸 Agreement 名区分，使用 -KEM 后缀）
        assertInstanceOf(com.flora.crypto.core.impl.AgreementBasedKem.class,
                CryptoProvider.resolve("ECDH-KEM"));
        assertInstanceOf(com.flora.crypto.core.impl.AgreementBasedKem.class,
                CryptoProvider.resolve("X25519-KEM"));
        // 确定性随机比特生成器（SP800-90A HMAC_DRBG）
        assertInstanceOf(com.flora.crypto.core.impl.HMacDrbg.class,
                CryptoProvider.resolve("HMAC_DRBG(HMac(SHA-256))"));
    }

    @Test
    void agreementBasedKemRoundTrip() {
        // 接收方生成 EC 密钥对，封装/解封装应得到一致的对称密钥
        com.flora.crypto.core.bridge.JdkAsymmetricKeyPairGenerator gen =
                com.flora.crypto.core.bridge.JdkAsymmetricKeyPairGenerator.of("EC");
        gen.init(new com.flora.crypto.core.impl.KeyGenerationParameterImpl(256));
        com.flora.crypto.core.interfaces.material.keypair.AsymmetricCipherKeyPair kp = gen.generateKeyPair();

        com.flora.crypto.core.interfaces.material.param.AsymmetricPublicKeyParameter pub =
                (com.flora.crypto.core.interfaces.material.param.AsymmetricPublicKeyParameter) kp.getPublic();
        com.flora.crypto.core.interfaces.material.param.AsymmetricPrivateKeyParameter priv =
                (com.flora.crypto.core.interfaces.material.param.AsymmetricPrivateKeyParameter) kp.getPrivate();

        com.flora.crypto.core.interfaces.algorithm.KeyEncapsulationMechanism kem =
                com.flora.crypto.core.impl.AgreementBasedKem.of("ECDH");
        com.flora.crypto.core.interfaces.material.kem.Encapsulator enc = kem.newEncapsulator(pub);
        com.flora.crypto.core.interfaces.material.kem.SecretWithEncapsulation swc = enc.encapsulate();
        com.flora.crypto.core.interfaces.material.kem.Decapsulator dec = kem.newDecapsulator(priv);
        byte[] secret2 = dec.decapsulate(swc.getEncapsulation());

        assertArrayEquals(swc.getSecret(), secret2);
    }

    @Test
    void jdkSignatureRoundTrip() {
        com.flora.crypto.core.bridge.JdkAsymmetricKeyPairGenerator gen =
                com.flora.crypto.core.bridge.JdkAsymmetricKeyPairGenerator.of("EC");
        gen.init(new com.flora.crypto.core.impl.KeyGenerationParameterImpl(256));
        com.flora.crypto.core.interfaces.material.keypair.AsymmetricCipherKeyPair kp = gen.generateKeyPair();

        byte[] digest = new byte[32];
        new java.security.SecureRandom().nextBytes(digest);

        com.flora.crypto.core.interfaces.algorithm.Signature signer =
                com.flora.crypto.core.bridge.JdkSignature.of("SHA256withECDSA");
        signer.init(true, kp.getPrivate());
        byte[] sig = signer.sign(digest);

        com.flora.crypto.core.interfaces.algorithm.Signature verifier =
                com.flora.crypto.core.bridge.JdkSignature.of("SHA256withECDSA");
        verifier.init(false, kp.getPublic());
        assertTrue(verifier.verify(digest, sig));
    }

    @Test
    void hmacDrbgDeterministic() {
        // 同熵同 nonce 应可复现；不同熵应产生不同输出
        com.flora.crypto.core.interfaces.algorithm.Mac mac =
                com.flora.crypto.core.bridge.JdkMac.of("HmacSHA256");
        byte[] entropy = new byte[32];
        byte[] nonce = new byte[16];
        new java.security.SecureRandom().nextBytes(entropy);
        new java.security.SecureRandom().nextBytes(nonce);

        com.flora.crypto.core.interfaces.algorithm.DeterministicRandomBitGenerator g1 =
                new com.flora.crypto.core.impl.HMacDrbg(mac, entropy.clone(), nonce.clone(), null);
        com.flora.crypto.core.interfaces.algorithm.DeterministicRandomBitGenerator g2 =
                new com.flora.crypto.core.impl.HMacDrbg(mac, entropy.clone(), nonce.clone(), null);
        byte[] out1 = new byte[64];
        byte[] out2 = new byte[64];
        assertEquals(512, g1.generate(out1));
        assertEquals(512, g2.generate(out2));
        assertArrayEquals(out1, out2);

        byte[] otherEntropy = entropy.clone();
        otherEntropy[0] ^= (byte) 0xFF;
        com.flora.crypto.core.interfaces.algorithm.DeterministicRandomBitGenerator g3 =
                new com.flora.crypto.core.impl.HMacDrbg(mac, otherEntropy, nonce.clone(), null);
        byte[] out3 = new byte[64];
        g3.generate(out3);
        assertFalse(java.util.Arrays.equals(out1, out3));
    }

    @Test
    void cbcWithXorEngineRoundTrip() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        byte[] plain = new byte[48];
        new SecureRandom().nextBytes(plain);

        CBCBlockCipher cbc = new CBCBlockCipher(new XorBlockCipher(key));
        cbc.init(true, new TestParameterWithIV(key, iv));
        byte[] cipher = cbc.update(plain);
        cbc.doFinal();

        CBCBlockCipher dec = new CBCBlockCipher(new XorBlockCipher(key));
        dec.init(false, new TestParameterWithIV(key, iv));
        byte[] back = dec.update(cipher);
        dec.doFinal();

        assertArrayEquals(plain, back);
    }

    @Test
    void sicWithXorEngineRoundTrip() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        byte[] plain = new byte[48];
        new SecureRandom().nextBytes(plain);

        SICBlockCipher ctr = new SICBlockCipher(new XorBlockCipher(key));
        ctr.init(true, new TestParameterWithIV(key, iv));
        byte[] cipher = ctr.update(plain);
        ctr.doFinal();

        SICBlockCipher dec = new SICBlockCipher(new XorBlockCipher(key));
        dec.init(false, new TestParameterWithIV(key, iv));
        byte[] back = dec.update(cipher);
        dec.doFinal();

        assertArrayEquals(plain, back);
    }

    @Test
    void paddedBufferedBlockCipherRoundTrip() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        byte[] plain = new byte[30]; // 非块对齐，需要填充

        PaddedBufferedBlockCipherWrapper enc = new PaddedBufferedBlockCipherWrapper(new XorBlockCipher(key));
        enc.init(true, new TestKeyParameter(key));
        byte[] cipher = enc.process(plain);

        PaddedBufferedBlockCipherWrapper dec = new PaddedBufferedBlockCipherWrapper(new XorBlockCipher(key));
        dec.init(false, new TestKeyParameter(key));
        byte[] back = dec.process(cipher);

        assertArrayEquals(plain, back);
    }

    @Test
    void paddedBufferedFactoryWiring() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        byte[] plain = new byte[37]; // 非块对齐
        new SecureRandom().nextBytes(plain);

        // 验证 DSL 组合器接线：construct 注入底层 BlockCipher，缺省填充时用 PKCS7
        com.flora.crypto.core.interfaces.algorithm.BufferedBlockCipher enc =
                PaddedBufferedBlockCipherWrapper.FACTORY.construct(
                        "PaddedBuffered", new XorBlockCipher(key));
        enc.init(true, new TestKeyParameter(key));
        byte[] cipher = enc.process(plain);

        com.flora.crypto.core.interfaces.algorithm.BufferedBlockCipher dec =
                PaddedBufferedBlockCipherWrapper.FACTORY.construct(
                        "PaddedBuffered", new XorBlockCipher(key));
        dec.init(false, new TestKeyParameter(key));
        byte[] back = dec.process(cipher);

        assertArrayEquals(plain, back);
    }

    @Test
    void xorEngineDirectRoundTrip() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        byte[] plain = new byte[16];
        new SecureRandom().nextBytes(plain);

        XorBlockCipher enc = new XorBlockCipher(key);
        byte[] out = new byte[16];
        enc.processBlock(plain, 0, out, 0);
        assertFalse(Arrays.equals(plain, out));

        XorBlockCipher dec = new XorBlockCipher(key);
        byte[] back = new byte[16];
        dec.processBlock(out, 0, back, 0);
        assertArrayEquals(plain, back);
    }

    private static void assertFalse(boolean cond) {
        if (cond) {
            throw new AssertionError("expected false");
        }
    }

    private static final class TestKeyParameter implements com.flora.crypto.core.interfaces.material.param.KeyParameter {
        private final byte[] key;

        TestKeyParameter(byte[] key) {
            this.key = key.clone();
        }

        @Override
        public byte[] getKey() {
            return key.clone();
        }
    }

    private static final class TestParameterWithIV
            implements com.flora.crypto.core.interfaces.material.param.ParameterWithIV {
        private final TestKeyParameter key;
        private final byte[] iv;

        TestParameterWithIV(byte[] key, byte[] iv) {
            this.key = new TestKeyParameter(key);
            this.iv = iv.clone();
        }

        @Override
        public CipherParameter getParameters() {
            return key;
        }

        @Override
        public byte[] getIV() {
            return iv.clone();
        }
    }
}
