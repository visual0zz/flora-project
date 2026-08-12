package com.flora.root.crypto.core.bridge;

import com.flora.root.common.register.AlgorithmFactoryRegister;
import com.flora.root.tag.ThreadFragile;

import com.flora.root.crypto.core.impl.AsymmetricKeyParameterImpl;
import com.flora.root.crypto.core.impl.SecretWithEncapsulationImpl;
import com.flora.root.crypto.core.interfaces.algorithm.KeyEncapsulationMechanism;
import com.flora.root.crypto.core.interfaces.material.kem.Decapsulator;
import com.flora.root.crypto.core.interfaces.material.kem.Encapsulator;
import com.flora.root.crypto.core.interfaces.material.kem.SecretWithEncapsulation;
import com.flora.root.crypto.core.interfaces.material.param.AsymmetricPrivateKeyParameter;
import com.flora.root.crypto.core.interfaces.material.param.AsymmetricPublicKeyParameter;
import com.flora.root.java.CheckUtil;

import javax.crypto.KEM;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import com.flora.root.common.register.AlgorithmComponent;
import com.flora.root.common.register.AlgorithmFactory;
import com.flora.root.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.root.crypto.core.interfaces.material.param.CipherParameter;

import java.util.Set;

/**
 * 把 JDK 自带的 {@link KEM}（ML-KEM，Java 21+）接入 newcore {@link KeyEncapsulationMechanism}。
 * <p>实现封装/解封装语义：发送方用接收方公钥封装出共享密钥与封装密文，接收方用私钥解封装还原。
 * 取代 {@code PlaceholderKem} 占位实现。</p>
 */
@ThreadFragile
public final class JdkKem implements KeyEncapsulationMechanism {

    private final String algorithm;
    private final KEM kem;

    private JdkKem(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.kem = KEM.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的 KEM 算法: " + algorithm, e);
        }
    }

    public static JdkKem of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkKem(algorithm);
    }

    public static final java.util.Set<String> SUPPORTED = java.util.Set.of("ML-KEM");

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    @Override
    public Encapsulator newEncapsulator(AsymmetricPublicKeyParameter publicKey) {
        try {
            return new JdkEncapsulator(algorithm,
                    kem.newEncapsulator(asKey(publicKey, java.security.PublicKey.class, "公钥")));
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalArgumentException("创建封装器失败: " + algorithm, e);
        }
    }

    @Override
    public Decapsulator newDecapsulator(AsymmetricPrivateKeyParameter privateKey) {
        try {
            return new JdkDecapsulator(algorithm,
                    kem.newDecapsulator(asKey(privateKey, java.security.PrivateKey.class, "私钥")));
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalArgumentException("创建解封装器失败: " + algorithm, e);
        }
    }

    private static <T extends Key> T asKey(Object params, Class<T> type, String what) {
        CheckUtil.notNull(params, "密钥参数不能为空");
        if (!(params instanceof AsymmetricKeyParameterImpl)) {
            throw new IllegalArgumentException("KEM 需要 AsymmetricKeyParameterImpl（" + what + "）");
        }
        Key key = ((AsymmetricKeyParameterImpl) params).getJdkKey();
        if (key == null || !type.isInstance(key)) {
            throw new IllegalArgumentException("KEM " + what + "类型不匹配: " + key);
        }
        return type.cast(key);
    }

    private static final class JdkEncapsulator implements Encapsulator {
        private final String algorithm;
        private final KEM.Encapsulator enc;

        JdkEncapsulator(String algorithm, KEM.Encapsulator enc) {
            this.algorithm = algorithm;
            this.enc = enc;
        }

        @Override
        public SecretWithEncapsulation encapsulate() {
            KEM.Encapsulated e = enc.encapsulate();
            return new SecretWithEncapsulationImpl(e.key().getEncoded(), e.encapsulation());
        }

        @Override
        public CipherParameter getPublicKey() {
            return null;
        }
    }

    private static final class JdkDecapsulator implements Decapsulator {
        private final String algorithm;
        private final KEM.Decapsulator dec;

        JdkDecapsulator(String algorithm, KEM.Decapsulator dec) {
            this.algorithm = algorithm;
            this.dec = dec;
        }

        @Override
        public byte[] decapsulate(byte[] encapsulation) {
            CheckUtil.notNull(encapsulation, "封装密文不能为空");
            try {
                return dec.decapsulate(encapsulation).getEncoded();
            } catch (javax.crypto.DecapsulateException e) {
                throw new IllegalStateException("解封装失败: " + algorithm, e);
            }
        }

        @Override
        public AsymmetricPrivateKeyParameter getPrivateKey() {
            return null;
        }
    }

    @Override
    public AlgorithmFactory<? extends KeyEncapsulationMechanism> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<KeyEncapsulationMechanism> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFactoryRegister> registerTo() {
            return CryptoAlgorithmFactoryRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return SUPPORTED;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<AlgorithmComponent>[] componentTypes() {
            return new Class[0];
        }

        @Override
        public KeyEncapsulationMechanism construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            return JdkKem.of(algorithmName);
        }
    };
}
