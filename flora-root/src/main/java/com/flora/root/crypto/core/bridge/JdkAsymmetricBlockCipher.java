package com.flora.root.crypto.core.bridge;

import com.flora.root.common.register.AlgorithmFactoryRegister;
import com.flora.root.tag.ThreadFragile;

import com.flora.root.crypto.core.impl.AsymmetricKeyParameterImpl;
import com.flora.root.crypto.core.interfaces.algorithm.AsymmetricBlockCipher;
import com.flora.root.crypto.core.interfaces.material.param.CipherParameter;
import com.flora.root.java.CheckUtil;

import javax.crypto.Cipher;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAKey;
import com.flora.root.common.register.AlgorithmComponent;
import com.flora.root.common.register.AlgorithmFactory;
import com.flora.root.crypto.core.CryptoAlgorithmFactoryRegister;
import java.util.Set;

/**
 * JDK 裸 RSA 原语接入 newcore {@link AsymmetricBlockCipher} 接口。
 * <p>只接受裸算法名 {@code "RSA"}，内部固定使用 {@code "RSA/ECB/NoPadding"} 变换——不做任何填充，
 * 等价于裸 {@code m^e mod n} / {@code m^d mod n}。填充（PKCS1v1.5/OAEP）由自研组合层编排。</p>
 */
@ThreadFragile
public final class JdkAsymmetricBlockCipher implements AsymmetricBlockCipher {

    private final String transformation;
    private final Cipher cipher;
    private int keyBitLength;

    private JdkAsymmetricBlockCipher(String algorithm) {
        this.transformation = algorithm + "/ECB/NoPadding";
        try {
            this.cipher = Cipher.getInstance(transformation);
        } catch (NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException e) {
            throw new IllegalArgumentException("不支持的非对称算法: " + algorithm, e);
        }
    }

    public static JdkAsymmetricBlockCipher of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        if (algorithm.indexOf('/') >= 0) {
            throw new IllegalArgumentException("只接受裸算法名（如 \"RSA\"），填充由自研组合层编排: " + algorithm);
        }
        return new JdkAsymmetricBlockCipher(algorithm);
    }

    public static final java.util.Set<String> SUPPORTED = java.util.Set.of("RSA");

    @Override
    public String getAlgorithmName() {
        return transformation;
    }

    @Override
    public void init(boolean forEncryption, CipherParameter params) {
        CheckUtil.notNull(params, "参数不能为空");
        Key key = asymmetricKey(params);
        this.keyBitLength = rsaBitLength(key);
        try {
            cipher.init(forEncryption ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("初始化非对称密码失败: " + transformation, e);
        }
    }

    @Override
    public int getInputBlockSize() {
        return keyBitLength / 8;
    }

    @Override
    public int getOutputBlockSize() {
        return keyBitLength / 8;
    }

    @Override
    public byte[] processBlock(byte[] in, int inOff, int len) {
        try {
            return cipher.doFinal(in, inOff, len);
        } catch (Exception e) {
            throw new IllegalStateException("处理非对称块失败: " + transformation, e);
        }
    }

    private static Key asymmetricKey(CipherParameter params) {
        if (params instanceof AsymmetricKeyParameterImpl impl && impl.getJdkKey() != null) {
            return impl.getJdkKey();
        }
        throw new IllegalArgumentException("需要 AsymmetricKeyParameterImpl（非对称密钥）");
    }

    private static int rsaBitLength(Key key) {
        if (key instanceof RSAKey) {
            return ((RSAKey) key).getModulus().bitLength();
        }
        return 0;
    }

    @Override
    public AlgorithmFactory<? extends AsymmetricBlockCipher> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<AsymmetricBlockCipher> FACTORY = new AlgorithmFactory<>() {
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
        public AsymmetricBlockCipher construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            return JdkAsymmetricBlockCipher.of(algorithmName);
        }
    };
}
