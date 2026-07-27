package com.flora.crypto.core;

/**
 * 签名 / 验签引擎接口。
 * <p>对应常见算法：SHA256withRSA / SHA256withECDSA / SHA512withRSA 等。</p>
 */
public interface Signer {

    /**
     * 初始化。
     *
     * @param forSigning 是否为签名（true 用私钥，false 用公钥验签）
     * @param params     非对称密钥参数
     */
    void init(boolean forSigning, CipherParameters params);

    /** @return 算法名 */
    String getAlgorithmName();

    void update(byte in);

    void update(byte[] in, int inOff, int len);

    /** @return 签名字节 */
    byte[] generateSignature();

    /** @return 验签是否通过 */
    boolean verifySignature(byte[] signature);
}
