package com.flora.crypto.core;

import com.flora.codec.HexUtil;
import com.flora.crypto.core.engine.JdkDigest;
import com.flora.crypto.core.mode.CBCBlockCipher;
import com.flora.crypto.core.mode.GCMBlockCipher;
import com.flora.crypto.core.padding.PKCS1v15Padding;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CryptoAbstractionTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    // ── Digest：SHA-256 已知答案向量 ──

    @Test
    void sha256KnownAnswer() {
        Digest d = CryptoProvider.digest("SHA-256");
        assertEquals(32, d.getDigestSize());
        d.update("abc".getBytes(), 0, 3);
        byte[] out = new byte[32];
        d.doFinal(out, 0);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                HexUtil.encodeHex(out));
    }

    // ── BlockCipher：自研 CBC + PKCS7 组合往返 ──

    @Test
    void aesCbcRoundTrip() {
        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(16);
        byte[] plain = "the quick brown fox".getBytes();

        PaddedBufferedBlockCipher enc = new PaddedBufferedBlockCipher(
                new CBCBlockCipher(CryptoProvider.blockCipher("AES")),
                CryptoProvider.blockCipherPadding("PKCS7"));
        enc.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] cipherText = enc.process(plain);

        PaddedBufferedBlockCipher dec = new PaddedBufferedBlockCipher(
                new CBCBlockCipher(CryptoProvider.blockCipher("AES")),
                CryptoProvider.blockCipherPadding("PKCS7"));
        dec.init(false, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] recovered = dec.process(cipherText);

        assertArrayEquals(plain, recovered);
    }

    // ── BlockCipher：自研 GCM 往返（认证标签校验）──

    @Test
    void aesGcmRoundTrip() {
        byte[] key = randomBytes(16);
        byte[] iv = randomBytes(12);
        byte[] plain = "authenticated encryption".getBytes();

        GCMBlockCipher enc = new GCMBlockCipher(CryptoProvider.blockCipher("AES"));
        enc.init(true, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] cipherText = new byte[enc.getOutputSize(plain.length)];
        int len = enc.processBytes(plain, 0, plain.length, cipherText, 0);
        len += enc.doFinal(cipherText, len);
        byte[] fullCipher = Arrays.copyOf(cipherText, len);

        GCMBlockCipher dec = new GCMBlockCipher(CryptoProvider.blockCipher("AES"));
        dec.init(false, new ParametersWithIV(new KeyParameter(key), iv));
        byte[] recovered = new byte[dec.getOutputSize(fullCipher.length)];
        int rlen = dec.processBytes(fullCipher, 0, fullCipher.length, recovered, 0);
        rlen += dec.doFinal(recovered, rlen);

        assertArrayEquals(plain, Arrays.copyOf(recovered, rlen));
    }

    // ── BufferedBlockCipher 装饰器：裸 AES 块对齐数据 ──

    @Test
    void bufferedBlockCipherDecorator() {
        byte[] key = randomBytes(16);
        byte[] plain = randomBytes(32); // 两块，块对齐

        BufferedBlockCipher enc = new BufferedBlockCipher(CryptoProvider.blockCipher("AES"));
        enc.init(true, new KeyParameter(key));
        byte[] cipherText = enc.process(plain);

        BufferedBlockCipher dec = new BufferedBlockCipher(CryptoProvider.blockCipher("AES"));
        dec.init(false, new KeyParameter(key));
        byte[] recovered = dec.process(cipherText);

        assertArrayEquals(plain, recovered);
        assertEquals(16, enc.getBlockSize());
    }

    // ── Mac：HmacSHA256 确定性 ──

    @Test
    void hmacSha256Deterministic() {
        byte[] key = randomBytes(32);
        byte[] data = "message".getBytes();

        Mac m1 = CryptoProvider.mac("HmacSHA256");
        m1.init(new KeyParameter(key));
        m1.update(data, 0, data.length);
        byte[] out1 = new byte[m1.getMacSize()];
        m1.doFinal(out1, 0);

        Mac m2 = CryptoProvider.mac("HmacSHA256");
        m2.init(new KeyParameter(key));
        m2.update(data, 0, data.length);
        byte[] out2 = new byte[m2.getMacSize()];
        m2.doFinal(out2, 0);

        assertArrayEquals(out1, out2);
        assertEquals(32, m1.getMacSize());
    }

    // ── AsymmetricBlockCipher：裸 RSA + 自研 PKCS1v1.5 填充往返 ──

    @Test
    void rsaRoundTrip() {
        KeyPair kp = CryptoProvider.keyPairGenerator("RSA").generate(2048);
        byte[] plain = "hello rsa world".getBytes();

        AsymmetricBlockCipher enc = new PaddedAsymmetricBlockCipher(
                CryptoProvider.asymmetricCipher("RSA"), new PKCS1v15Padding());
        enc.init(true, new AsymmetricKeyParameter(kp.getPublic()));
        byte[] cipherText = enc.processBlock(plain, 0, plain.length);

        AsymmetricBlockCipher dec = new PaddedAsymmetricBlockCipher(
                CryptoProvider.asymmetricCipher("RSA"), new PKCS1v15Padding());
        dec.init(false, new AsymmetricKeyParameter(kp.getPrivate()));
        byte[] recovered = dec.processBlock(cipherText, 0, cipherText.length);

        assertArrayEquals(plain, recovered);
        assertTrue(enc.getInputBlockSize() > 0);
        assertEquals(256, enc.getOutputBlockSize());
    }

    // ── CryptoProvider 按名取得各组件（仅原语）──

    @Test
    void providerResolvesAllFamilies() {
        assertNotNull(CryptoProvider.digest("SHA-256"));
        assertNotNull(CryptoProvider.blockCipher("AES"));
        assertNotNull(CryptoProvider.asymmetricCipher("RSA"));
        assertNotNull(CryptoProvider.mac("HmacSHA256"));
        assertNotNull(CryptoProvider.keyPairGenerator("RSA"));
    }

    // ── 原语入口拒绝 JDK 组合变换字符串 ──

    @Test
    void rejectsTransformationStrings() {
        assertThrows(IllegalArgumentException.class,
                () -> CryptoProvider.blockCipher("AES/CBC/PKCS5Padding"));
        assertThrows(IllegalArgumentException.class,
                () -> CryptoProvider.asymmetricCipher("RSA/ECB/PKCS1Padding"));
    }

    // ── 自定义注册表：按名优先返回自定义实现，未命中回退 JDK ──

    /** 一个最小的自定义 Digest 实现，仅用于验证注册表优先逻辑。 */
    private static final class NoopDigest implements Digest {
        private final String name;
        private final int size;

        NoopDigest(String name, int size) {
            this.name = name;
            this.size = size;
        }

        @Override
        public String getAlgorithmName() {
            return name;
        }

        @Override
        public int getDigestSize() {
            return size;
        }

        @Override
        public void update(byte in) {
        }

        @Override
        public void update(byte[] in, int inOff, int len) {
        }

        @Override
        public int doFinal(byte[] out, int outOff) {
            return 0;
        }

        @Override
        public void reset() {
        }
    }

    @Test
    void customRegistryPreferredAndFallsBack() {
        // 未注册时回退到 JDK 适配器
        assertInstanceOf(JdkDigest.class, CryptoProvider.digest("SHA-256"));

        // 注册自定义实现后，同名优先返回自定义实例
        CryptoProvider.registerDigest("MyHash", () -> new NoopDigest("MyHash", 7));
        Digest custom = CryptoProvider.digest("MyHash");
        assertEquals("MyHash", custom.getAlgorithmName());
        assertEquals(7, custom.getDigestSize());
        assertInstanceOf(NoopDigest.class, custom);

        // 覆盖同名 JDK 算法：注册 "SHA-1" 后优先返回自定义（prefer 自己的算法）。
        // 注意：注册表是 JVM 全局的，此处故意避开其它测试依赖的 "SHA-256" 以免污染。
        CryptoProvider.registerDigest("SHA-1", () -> new NoopDigest("SHA-1", 9));
        Digest overridden = CryptoProvider.digest("SHA-1");
        assertInstanceOf(NoopDigest.class, overridden);
        assertEquals(9, overridden.getDigestSize());
    }
}
