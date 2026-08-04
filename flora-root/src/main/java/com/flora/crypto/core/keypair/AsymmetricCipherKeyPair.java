package com.flora.crypto.core.keypair;

import com.flora.java.CheckUtil;

/**
 * 轻量级非对称密钥对。
 * <p>与 JDK 的 {@code KeyPair} 不同，这里直接持有本项目的 {@link AsymmetricKeyParameter}
 * （公钥/私钥各一份），便于在接口体系内流转，无需依赖 {@code java.security.KeyPair}。</p>
 */
public final class AsymmetricCipherKeyPair {

    private final AsymmetricKeyParameter publicParam;
    private final AsymmetricKeyParameter privateParam;

    public AsymmetricCipherKeyPair(AsymmetricKeyParameter publicParam, AsymmetricKeyParameter privateParam) {
        CheckUtil.notNull(publicParam, "公钥参数不能为空");
        CheckUtil.notNull(privateParam, "私钥参数不能为空");
        this.publicParam = publicParam;
        this.privateParam = privateParam;
    }

    public AsymmetricKeyParameter getPublic() {
        return publicParam;
    }

    public AsymmetricKeyParameter getPrivate() {
        return privateParam;
    }
}
