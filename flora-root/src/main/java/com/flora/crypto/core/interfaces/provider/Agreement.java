package com.flora.crypto.core.interfaces.provider;
import com.flora.crypto.core.interfaces.CipherParameters;

/**
 * 密钥协商（Key Agreement）接口（Bouncy Castle 风格）。
 * <p>对应常见方案：ECDH、DH、X25519/X448。双方各自持有私钥，用对方公钥计算出共享密钥材料。
 * JDK 的 {@code KeyAgreement} 已具备该能力，由 {@code JdkAgreement} 适配。</p>
 */
public interface Agreement extends AlgorithmFamily {

    /**
     * 用本地私钥初始化协商。
     *
     * @param params 持有的私钥参数（通常是 {@link AsymmetricKeyParameter}）
     */
    void init(CipherParameters params);

    /**
     * 用对方公钥执行协商，返回共享密钥材料。
     *
     * @param pubKey 对方公钥参数
     * @return 共享密钥字节
     */
    byte[] calculateAgreement(CipherParameters pubKey);
}
