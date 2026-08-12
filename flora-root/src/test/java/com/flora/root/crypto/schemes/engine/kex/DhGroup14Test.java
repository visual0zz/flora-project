package com.flora.root.crypto.schemes.engine.kex;

import com.flora.root.crypto.core.interfaces.algorithm.EntropySource;
import org.junit.jupiter.api.Test;

import com.flora.root.crypto.schemes.SchemeContext;
import com.flora.root.crypto.schemes.SchemeProvider;
import com.flora.root.crypto.schemes.keyexchange.KeyExchange;

import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DhGroup14} 二进制正确性验证。
 *
 * <p>RFC 3526 Group 14 仅定义群参数（p/g），未附 Known Answer Test 向量；且 RFC 5114 的
 * 2048-bit MODP 群与 Group 14 的 p 不同，不能直接套用其向量。本测试采用密码学实现测试公认的
 * 做法：以带种子的 {@link SecureRandom} 生成确定性密钥对（向量可复现），并以独立的
 * {@link BigInteger#modPow} 实现双重计算（{@code Z = g^(xa*xb) mod p}），交叉验证
 * {@code DhGroup14} 借助 JDK {@code KeyAgreement "DH"} 完成的模幂、字节序与共享密钥。</p>
 *
 * <p>注意：JDK 26 的 FFC 校验会对「导入」的 {@code DHPrivateKeySpec} 私有指数做归约
 * （{@code x → x mod q} 乃至塌缩），因此本测试改用生成式密钥对并读取其实际 {@code x}，
 * 以走通与生产一致的真实协商路径；并以「共享密钥为大数、非平凡值」断言排除塌缩假通过。</p>
 */
class DhGroup14Test {

    // 固定种子，保证向量可复现（同 JDK 版本下结果稳定）。仅为测试用途，非真实密钥。
    private static final long SEED_A = 0x5a3c2b1a09f8e7d6L;
    private static final long SEED_B = 0x1a2b3c4d5e6f7081L;

    private static final BigInteger P = DhGroup14.modulus();
    private static final BigInteger G = DhGroup14.generator();
    private static final BigInteger Q = P.subtract(BigInteger.ONE).divide(BigInteger.TWO);

    private static KeyPair generateKeyPair(long seed) throws Exception {
        byte[] seedBytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            seedBytes[7 - i] = (byte) (seed >>> (8 * i));
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
        kpg.initialize(new DHParameterSpec(P, G), new SecureRandom(seedBytes));
        return kpg.generateKeyPair();
    }

    /** 归一化为 p 的字节长度（大端无符号），消除 JDK 生成密钥可能的首字节 0x00 差异。 */
    private static byte[] mpint(BigInteger v) {
        byte[] raw = v.toByteArray();
        int len = (P.bitLength() + 7) / 8;
        if (raw.length == len + 1 && raw[0] == 0) {
            byte[] trimmed = new byte[len];
            System.arraycopy(raw, 1, trimmed, 0, len);
            return trimmed;
        }
        if (raw.length < len) {
            byte[] padded = new byte[len];
            System.arraycopy(raw, 0, padded, len - raw.length, raw.length);
            return padded;
        }
        return raw;
    }

    @Test
    void knownVectorCrossCheck() throws Exception {
        KeyPair kpA = generateKeyPair(SEED_A);
        KeyPair kpB = generateKeyPair(SEED_B);

        BigInteger xa = ((DHPrivateKey) kpA.getPrivate()).getX();
        BigInteger xb = ((DHPrivateKey) kpB.getPrivate()).getX();
        BigInteger ya = ((DHPublicKey) kpA.getPublic()).getY();
        BigInteger yb = ((DHPublicKey) kpB.getPublic()).getY();

        // 私有指数必须落在校验子群 [2, q-2] 内，且非平凡（排除 JDK 将 x 塌缩为 ≡1 的情形）
        assertTrue(xa.compareTo(BigInteger.TWO) > 0 && xa.compareTo(Q) < 0, "xa 应落在校验子群内");
        assertTrue(xb.compareTo(BigInteger.TWO) > 0 && xb.compareTo(Q) < 0, "xb 应落在校验子群内");

        // 独立性校验：JDK 生成的公钥 y 应与 g^x mod p 一致（验证读到的 x/y 自洽）
        assertEquals(0, ya.compareTo(G.modPow(xa, P)), "JDK 公钥 yA 应与 g^xa 一致");
        assertEquals(0, yb.compareTo(G.modPow(xb, P)), "JDK 公钥 yB 应与 g^xb 一致");

        // 独立 BigInteger 实现计算的预期共享密钥 Z = g^(xa*xb) mod p
        BigInteger expectedZ = yb.modPow(xa, P);
        assertTrue(expectedZ.bitLength() > P.bitLength() - 16, "共享密钥应为大数，非平凡值");

        // 实现侧（真实 JdkAgreement 路径）：a 注入确定性私钥，对端贡献 yb
        PrivateKey privA = kpA.getPrivate();
        PrivateKey privB = kpB.getPrivate();

        DhGroup14 a = new DhGroup14();
        a.init(privA);                            // 测试钩子：注入私钥
        byte[] eA = a.step(null);                // 本方公开贡献 e = g^xa
        assertArrayEquals(mpint(ya), mpint(new BigInteger(1, eA)), "本方公开贡献应与 g^xa 一致");
        a.step(mpint(yb));                       // 处理对端贡献，算共享密钥 K
        byte[] kA = a.sharedSecret();

        // 实现侧：b 注入确定性私钥，对端贡献 ya
        DhGroup14 b = new DhGroup14();
        b.init(privB);
        b.step(mpint(ya));
        byte[] kB = b.sharedSecret();

        // 双方共享密钥应一致（对称密钥交换）
        assertArrayEquals(mpint(new BigInteger(1, kA)), mpint(new BigInteger(1, kB)), "双方算出的共享密钥应一致");

        // 与独立 BigInteger 实现一致（二进制正确）
        assertArrayEquals(mpint(expectedZ), mpint(new BigInteger(1, kA)),
            "共享密钥应与独立 BigInteger 实现计算的 Z 一致");
    }

    /**
     * 调用方经 {@link SchemeProvider#keyExchange(String)} 入口取实例，并完成一次
     * 真实（随机密钥）的两方密钥交换，验证「注册 → 取实例 → 多轮推进 → 共享密钥一致」全链路。
     */
    @Test
    void entryPointResolvesAndExchanges() {
        KeyExchange alice = SchemeProvider.keyExchange("diffie-hellman-group14");
        KeyExchange bob = SchemeProvider.keyExchange("diffie-hellman-group14");
        assertTrue(alice instanceof DhGroup14, "入口应返回 DhGroup14 实例");

        SchemeContext ctx = new SchemeContext() {
            @Override
            public EntropySource entropy() {
                return null; // DhGroup14 使用 JDK KeyPairGenerator 自管随机性，无需外部熵
            }
        };
        alice.init(ctx);
        bob.init(ctx);

        byte[] eA = alice.step(null);   // 首轮：alice 公开贡献
        byte[] eB = bob.step(eA);       // bob 收 alice 贡献，返回自身贡献（bob 完成）
        alice.step(eB);                 // alice 收 bob 贡献（alice 完成）

        assertTrue(alice.isComplete() && bob.isComplete(), "双方均应标记完成");
        assertArrayEquals(alice.sharedSecret(), bob.sharedSecret(),
            "经入口获取的实例应协商出一致共享密钥");
    }
}
