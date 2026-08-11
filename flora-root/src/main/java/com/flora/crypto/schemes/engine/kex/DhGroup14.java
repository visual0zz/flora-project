package com.flora.crypto.schemes.engine.kex;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.newcore.bridge.JdkAgreement;
import com.flora.crypto.newcore.impl.AsymmetricKeyParameterImpl;
import com.flora.crypto.newcore.interfaces.algorithm.Agreement;
import com.flora.crypto.schemes.SchemeAlgorithmFamilyRegister;
import com.flora.crypto.schemes.SchemeContext;
import com.flora.crypto.schemes.SchemeException;
import com.flora.crypto.schemes.keyexchange.KeyExchange;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Set;
import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;

/**
 * Diffie-Hellman Group 14（RFC 3526, 2048-bit MODP）密钥交换。
 * <p>作为 {@link KeyExchange} 算法级协议的示例实现：底层模幂运算委托给 core 的
 * {@link Agreement}（JDK {@code KeyAgreement "DH"}），本类只编排「生成 e → 收 f → 算 K」。
 * 群参数 p/g 为 RFC 3526 Group 14 标准常量（生成元 g = 2）。</p>
 * <p>作为 {@code KeyExchange} 抽象的可落地示例，已通过 {@code DhGroup14Test} 验证二进制正确性
 * （带种子密钥对生成 + 独立 {@code BigInteger} 模幂交叉验证）。对端贡献与共享密钥均使用
 * BigInteger 原始字节，SSH 的 mpint 线格式编解码由上层组合级编排负责。</p>
 */
public final class DhGroup14 implements KeyExchange {

    private static final byte[] G = {2};
    private static final byte[] P = {(byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xC9, (byte) 0x0F, (byte) 0xDA,
        (byte) 0xA2, (byte) 0x21, (byte) 0x68, (byte) 0xC2, (byte) 0x34, (byte) 0xC4, (byte) 0xC6,
        (byte) 0x62, (byte) 0x8B, (byte) 0x80, (byte) 0xDC, (byte) 0x1C, (byte) 0xD1, (byte) 0x29,
        (byte) 0x02, (byte) 0x4E, (byte) 0x08, (byte) 0x8A, (byte) 0x67, (byte) 0xCC, (byte) 0x74,
        (byte) 0x02, (byte) 0x0B, (byte) 0xBE, (byte) 0xA6, (byte) 0x3B, (byte) 0x13, (byte) 0x9B,
        (byte) 0x22, (byte) 0x51, (byte) 0x4A, (byte) 0x08, (byte) 0x79, (byte) 0x8E, (byte) 0x34,
        (byte) 0x04, (byte) 0xDD, (byte) 0xEF, (byte) 0x95, (byte) 0x19, (byte) 0xB3, (byte) 0xCD,
        (byte) 0x3A, (byte) 0x43, (byte) 0x1B, (byte) 0x30, (byte) 0x2B, (byte) 0x0A, (byte) 0x6D,
        (byte) 0xF2, (byte) 0x5F, (byte) 0x14, (byte) 0x37, (byte) 0x4F, (byte) 0xE1, (byte) 0x35,
        (byte) 0x6D, (byte) 0x6D, (byte) 0x51, (byte) 0xC2, (byte) 0x45, (byte) 0xE4, (byte) 0x85,
        (byte) 0xB5, (byte) 0x76, (byte) 0x62, (byte) 0x5E, (byte) 0x7E, (byte) 0xC6, (byte) 0xF4,
        (byte) 0x4C, (byte) 0x42, (byte) 0xE9, (byte) 0xA6, (byte) 0x37, (byte) 0xED, (byte) 0x6B,
        (byte) 0x0B, (byte) 0xFF, (byte) 0x5C, (byte) 0xB6, (byte) 0xF4, (byte) 0x06, (byte) 0xB7,
        (byte) 0xED, (byte) 0xEE, (byte) 0x38, (byte) 0x6B, (byte) 0xFB, (byte) 0x5A, (byte) 0x89,
        (byte) 0x9F, (byte) 0xA5, (byte) 0xAE, (byte) 0x9F, (byte) 0x24, (byte) 0x11, (byte) 0x7C,
        (byte) 0x4B, (byte) 0x1F, (byte) 0xE6, (byte) 0x49, (byte) 0x28, (byte) 0x66, (byte) 0x51,
        (byte) 0xEC, (byte) 0xE4, (byte) 0x5B, (byte) 0x3D, (byte) 0xC2, (byte) 0x00, (byte) 0x7C,
        (byte) 0xB8, (byte) 0xA1, (byte) 0x63, (byte) 0xBF, (byte) 0x05, (byte) 0x98, (byte) 0xDA,
        (byte) 0x48, (byte) 0x36, (byte) 0x1C, (byte) 0x55, (byte) 0xD3, (byte) 0x9A, (byte) 0x69,
        (byte) 0x16, (byte) 0x3F, (byte) 0xA8, (byte) 0xFD, (byte) 0x24, (byte) 0xCF, (byte) 0x5F,
        (byte) 0x83, (byte) 0x65, (byte) 0x5D, (byte) 0x23, (byte) 0xDC, (byte) 0xA3, (byte) 0xAD,
        (byte) 0x96, (byte) 0x1C, (byte) 0x62, (byte) 0xF3, (byte) 0x56, (byte) 0x20, (byte) 0x85,
        (byte) 0x52, (byte) 0xBB, (byte) 0x9E, (byte) 0xD5, (byte) 0x29, (byte) 0x07, (byte) 0x70,
        (byte) 0x96, (byte) 0x96, (byte) 0x6D, (byte) 0x67, (byte) 0x0C, (byte) 0x35, (byte) 0x4E,
        (byte) 0x4A, (byte) 0xBC, (byte) 0x98, (byte) 0x04, (byte) 0xF1, (byte) 0x74, (byte) 0x6C,
        (byte) 0x08, (byte) 0xCA, (byte) 0x18, (byte) 0x21, (byte) 0x7C, (byte) 0x32, (byte) 0x90,
        (byte) 0x5E, (byte) 0x46, (byte) 0x2E, (byte) 0x36, (byte) 0xCE, (byte) 0x3B, (byte) 0xE3,
        (byte) 0x9E, (byte) 0x77, (byte) 0x2C, (byte) 0x18, (byte) 0x0E, (byte) 0x86, (byte) 0x03,
        (byte) 0x9B, (byte) 0x27, (byte) 0x83, (byte) 0xA2, (byte) 0xEC, (byte) 0x07, (byte) 0xA2,
        (byte) 0x8F, (byte) 0xB5, (byte) 0xC5, (byte) 0x5D, (byte) 0xF0, (byte) 0x6F, (byte) 0x4C,
        (byte) 0x52, (byte) 0xC9, (byte) 0xDE, (byte) 0x2B, (byte) 0xCB, (byte) 0xF6, (byte) 0x95,
        (byte) 0x58, (byte) 0x17, (byte) 0x18, (byte) 0x39, (byte) 0x95, (byte) 0x49, (byte) 0x7C,
        (byte) 0xEA, (byte) 0x95, (byte) 0x6A, (byte) 0xE5, (byte) 0x15, (byte) 0xD2, (byte) 0x26,
        (byte) 0x18, (byte) 0x98, (byte) 0xFA, (byte) 0x05, (byte) 0x10, (byte) 0x15, (byte) 0x72,
        (byte) 0x8E, (byte) 0x5A, (byte) 0x8A, (byte) 0xAC, (byte) 0xAA, (byte) 0x68, (byte) 0xFF,
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    private Agreement agreement;
    private byte[] e;
    private byte[] sharedSecret;
    private boolean complete;
    private boolean ownSent;

    /** 群质数 p（RFC 3526 Group 14），包级可见，供测试与组合级编排获取标准群参数。 */
    static BigInteger modulus() {
        return new BigInteger(1, P);
    }

    /** 生成元 g = 2。 */
    static BigInteger generator() {
        return new BigInteger(1, G);
    }

    @Override
    public String getAlgorithmName() {
        return "diffie-hellman-group14";
    }

    @Override
    public void init(SchemeContext ctx) {
        try {
            BigInteger p = new BigInteger(1, P);
            BigInteger g = new BigInteger(1, G);
            KeyPairGenerator gen = KeyPairGenerator.getInstance("DH");
            gen.initialize(new DHParameterSpec(p, g));
            KeyPair kp = gen.generateKeyPair();
            this.agreement = JdkAgreement.of("DH");
            this.agreement.init(AsymmetricKeyParameterImpl.fromPrivate(kp.getPrivate()));
            this.e = ((javax.crypto.interfaces.DHPublicKey) kp.getPublic()).getY().toByteArray();
            this.complete = false;
            this.ownSent = false;
            this.sharedSecret = null;
        } catch (Exception ex) {
            throw new SchemeException("DH Group14 初始化失败", ex);
        }
    }

    /**
     * 测试/特殊场景钩子：以指定私钥（而非随机生成）初始化，用于向量验证。
     * 常规协议流程应使用 {@link #init(SchemeContext)}。
     * <p>实现内部以注入的 {@link PrivateKey} 构造 {@code Agreement} 并据其 {@code x} 推导
     * 本方公开贡献 {@code e = g^x mod p}，使 {@code e} 与共享密钥使用同一 {@code x} 保持一致。</p>
     * <p>注意：JDK 26 的 FFC 校验会对「导入」的 {@code DHPrivateKeySpec} 私有指数做归约
     * （{@code x → x mod q} 乃至塌缩），故调用方应传入由 {@code KeyPairGenerator} 生成的
     * {@link PrivateKey}（如带种子 {@link java.security.SecureRandom} 以复现向量），而非手捏的
     * 裸指数，否则 {@code x} 会被改写、向量不可预期。</p>
     *
     * @param privateKey 预生成的 DH 私钥（其 {@code x} 用于推导 {@code e} 与共享密钥）
     */
    void init(PrivateKey privateKey) {
        try {
            BigInteger p = new BigInteger(1, P);
            BigInteger g = new BigInteger(1, G);
            this.agreement = JdkAgreement.of("DH");
            this.agreement.init(AsymmetricKeyParameterImpl.fromPrivate(privateKey));
            BigInteger x = ((DHPrivateKey) privateKey).getX();
            this.e = g.modPow(x, p).toByteArray();
            this.complete = false;
            this.ownSent = false;
            this.sharedSecret = null;
        } catch (Exception ex) {
            throw new SchemeException("DH Group14 私钥初始化失败", ex);
        }
    }

    @Override
    public byte[] step(byte[] peerContribution) {
        if (peerContribution == null) {
            // 首轮：发出本方公开贡献（发起方第一条消息）
            if (ownSent) {
                return null;
            }
            ownSent = true;
            return e;
        }
        try {
            BigInteger p = new BigInteger(1, P);
            BigInteger g = new BigInteger(1, G);
            DHPublicKeySpec spec = new DHPublicKeySpec(new BigInteger(1, peerContribution), p, g);
            java.security.PublicKey peerPub = KeyFactory.getInstance("DH").generatePublic(spec);
            this.sharedSecret = agreement.calculateAgreement(AsymmetricKeyParameterImpl.fromPublic(peerPub));
            this.complete = true;
            // 若本方贡献尚未发出（响应方），则在本步一并发出；否则已无消息可发
            if (ownSent) {
                return null;
            }
            ownSent = true;
            return e;
        } catch (Exception ex) {
            throw new SchemeException("DH Group14 协商失败", ex);
        }
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public byte[] sharedSecret() {
        if (!complete) {
            throw new SchemeException("密钥交换未完成");
        }
        return sharedSecret;
    }

    @Override
    public AlgorithmFactory<? extends KeyExchange> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<KeyExchange> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFamilyRegister> registerTo() {
            return SchemeAlgorithmFamilyRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("diffie-hellman-group14");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<? extends AlgorithmComponent>[] componentTypes() {
            return new Class[0];
        }

        @Override
        public KeyExchange construct(String algorithmName, AlgorithmComponent... components) {
            return new DhGroup14();
        }
    };
}
