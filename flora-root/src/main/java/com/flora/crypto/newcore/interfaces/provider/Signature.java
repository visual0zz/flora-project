package com.flora.crypto.newcore.interfaces.provider;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.crypto.newcore.interfaces.param.CipherParameters;

/**
 * 数字签名接口。
 * <p>对应常见方案：RSASSA / ECDSA / EdDSA。签名方用私钥对摘要签名，验签方用公钥校验。
 * 区别于 {@link Mac}（对称、共享密钥），签名是非对称的、可公开验签且不可抵赖。</p>
 */
public interface Signature extends Algorithm<AlgorithmFactory<? extends Signature>> {

    void init(boolean forSigning, CipherParameters params);

    /** @return 算法名，如 {@code "SHA256withRSA"} */
    String getAlgorithmName();

    /**
     * 对已完成摘要计算的 {@code digest} 签名。
     *
     * @param digest 摘要字节
     * @return 签名值
     */
    byte[] sign(byte[] digest);

    /**
     * 校验签名。
     *
     * @param digest 摘要字节
     * @param signature 待验签名
     * @return 验签是否通过
     */
    boolean verify(byte[] digest, byte[] signature);
}
