package com.flora.crypto.newcore.interfaces.provider;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.crypto.newcore.interfaces.param.CipherParameters;

/**
 * 密钥协商（Key Agreement）接口。
 * <p>对应常见方案：ECDH、DH、X25519/X448。双方各自持有私钥，用对方公钥计算出共享密钥材料。</p>
 */
public interface Agreement extends Algorithm<AlgorithmFactory<? extends Agreement>> {

    /**
     * 用本地私钥初始化协商。
     *
     * @param params 持有的私钥参数
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
