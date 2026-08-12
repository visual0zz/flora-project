package com.flora.crypto.core.bridge;

import com.flora.tag.ThreadFragile;

import com.flora.crypto.core.impl.AsymmetricKeyParameterImpl;
import com.flora.crypto.core.interfaces.algorithm.Agreement;
import com.flora.crypto.core.interfaces.material.param.AsymmetricPrivateKeyParameter;
import com.flora.crypto.core.interfaces.material.param.AsymmetricPublicKeyParameter;
import com.flora.java.CheckUtil;

import javax.crypto.KeyAgreement;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import com.flora.common.register.AlgorithmComponent;
import com.flora.common.register.AlgorithmFactory;
import com.flora.common.register.AlgorithmFactoryRegister;
import com.flora.crypto.core.CryptoAlgorithmFactoryRegister;
import java.util.Set;

/**
 * 把 JDK 的 {@link KeyAgreement} 接入 newcore {@link Agreement} 接口（ECDH / DH / X25519 等）。
 */
@ThreadFragile
public final class JdkAgreement implements Agreement {

    private final String algorithm;
    private final KeyAgreement agreement;
    private Key privateKey;

    private JdkAgreement(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.agreement = KeyAgreement.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的密钥协商算法: " + algorithm, e);
        }
    }

    public static JdkAgreement of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkAgreement(algorithm);
    }

    public static final java.util.Set<String> SUPPORTED = java.util.Set.of("ECDH", "X25519", "X448", "DH");

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    @Override
    public void init(AsymmetricPrivateKeyParameter params) {
        CheckUtil.notNull(params, "私钥参数不能为空");
        this.privateKey = asKey(params, java.security.PrivateKey.class, "私钥");
    }

    @Override
    public byte[] calculateAgreement(AsymmetricPublicKeyParameter pubKey) {
        CheckUtil.notNull(pubKey, "对方公钥参数不能为空");
        Key pub = asKey(pubKey, java.security.PublicKey.class, "公钥");
        try {
            agreement.init(privateKey);
            agreement.doPhase(pub, true);
            return agreement.generateSecret();
        } catch (Exception e) {
            throw new IllegalStateException("密钥协商失败: " + algorithm, e);
        }
    }

    private static <T extends Key> T asKey(Object params, Class<T> type, String what) {
        CheckUtil.notNull(params, "密钥参数不能为空");
        if (!(params instanceof AsymmetricKeyParameterImpl)) {
            throw new IllegalArgumentException("密钥协商需要 AsymmetricKeyParameterImpl（" + what + "）");
        }
        Key key = ((AsymmetricKeyParameterImpl) params).getJdkKey();
        if (key == null) {
            throw new IllegalArgumentException("密钥协商缺少底层 JDK 密钥（" + what + "）");
        }
        if (!type.isInstance(key)) {
            throw new IllegalArgumentException("密钥协商 " + what + "类型不匹配: " + key.getClass().getName());
        }
        return type.cast(key);
    }

    @Override
    public AlgorithmFactory<? extends Agreement> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<Agreement> FACTORY = new AlgorithmFactory<>() {
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
        public Agreement construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            return JdkAgreement.of(algorithmName);
        }
    };
}
