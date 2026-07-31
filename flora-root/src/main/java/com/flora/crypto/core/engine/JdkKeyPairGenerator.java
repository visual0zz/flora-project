package com.flora.crypto.core.engine;
import com.flora.tag.ThreadFragile;

import com.flora.java.CheckUtil;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * 把 JDK 自带的 {@link KeyPairGenerator} 接入统一的工厂式便捷封装。
 * <p>用于生成 RSA / EC 等非对称密钥对，配合 {@link JdkAsymmetricBlockCipher} 使用。</p>
 */
@ThreadFragile
public final class JdkKeyPairGenerator {

    private final KeyPairGenerator kpg;

    private JdkKeyPairGenerator(String algorithm) {
        try {
            this.kpg = KeyPairGenerator.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的密钥对算法: " + algorithm, e);
        }
    }

    public static JdkKeyPairGenerator of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkKeyPairGenerator(algorithm);
    }

    public KeyPair generate(int keySize) {
        kpg.initialize(keySize);
        return kpg.generateKeyPair();
    }

    public KeyPair generate() {
        return kpg.generateKeyPair();
    }
}
