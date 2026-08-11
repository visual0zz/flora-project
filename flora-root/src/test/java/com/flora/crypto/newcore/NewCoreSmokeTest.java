package com.flora.crypto.newcore;

import com.flora.common.algorithm.AlgorithmFamily;
import com.flora.crypto.newcore.combinator.PaddedBufferedBlockCipher;
import com.flora.crypto.newcore.interfaces.algorithm.BlockCipher;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameter;
import com.flora.crypto.newcore.mode.CBCBlockCipher;
import com.flora.crypto.newcore.mode.SICBlockCipher;
import com.flora.crypto.newcore.padding.PKCS7Padding;
import com.flora.crypto.newcore.padding.ZeroBytePadding;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
        public AlgorithmFamily<? extends BlockCipher> factory() {
            throw new UnsupportedOperationException("测试桩不参与注册");
        }
    }

    @Test
    void dslRegistrationAndResolution() {
        CryptoProvider.register(PKCS7Padding.FAMILY);
        CryptoProvider.register(ZeroBytePadding.FAMILY);

        Object pkcs7 = CryptoProvider.resolve("PKCS7");
        assertInstanceOf(PKCS7Padding.class, pkcs7);
        assertEquals("PKCS7", ((PKCS7Padding) pkcs7).getAlgorithmName());

        Object zero = CryptoProvider.resolve("ZeroByte");
        assertInstanceOf(ZeroBytePadding.class, zero);
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
        byte[] cipher = cbc.process(plain);

        CBCBlockCipher dec = new CBCBlockCipher(new XorBlockCipher(key));
        dec.init(false, new TestParameterWithIV(key, iv));
        byte[] back = dec.process(cipher);

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
        byte[] cipher = ctr.process(plain);

        SICBlockCipher dec = new SICBlockCipher(new XorBlockCipher(key));
        dec.init(false, new TestParameterWithIV(key, iv));
        byte[] back = dec.process(cipher);

        assertArrayEquals(plain, back);
    }

    @Test
    void paddedBufferedBlockCipherRoundTrip() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        byte[] plain = new byte[30]; // 非块对齐，需要填充

        PaddedBufferedBlockCipher enc = new PaddedBufferedBlockCipher(new XorBlockCipher(key));
        enc.init(true, new TestKeyParameter(key));
        byte[] cipher = enc.process(plain);

        PaddedBufferedBlockCipher dec = new PaddedBufferedBlockCipher(new XorBlockCipher(key));
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

    private static final class TestKeyParameter implements com.flora.crypto.newcore.interfaces.material.param.KeyParameter {
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
            implements com.flora.crypto.newcore.interfaces.material.param.ParameterWithIV {
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
