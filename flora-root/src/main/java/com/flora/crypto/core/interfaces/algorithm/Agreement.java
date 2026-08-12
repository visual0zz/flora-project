package com.flora.crypto.core.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.crypto.core.interfaces.material.param.AsymmetricPrivateKeyParameter;
import com.flora.crypto.core.interfaces.material.param.AsymmetricPublicKeyParameter;

/**
 * 密钥协商（Key Agreement）接口。
 * <p>对应常见方案：ECDH、DH、X25519/X448。双方各自持有私钥，用对方公钥计算出共享密钥材料。</p>
 * <p>与旧框架把公私钥都收进笼统的 {@code CipherParameter} 不同，本接口把「需要私钥」「需要公钥」
 * 直接写进签名：{@link #init} 收私钥参数、{@link #calculateAgreement} 收公钥参数，
 * 编译期即排除公私钥混用（设计参照 BouncyCastle 的
 * {@code AsymmetricPublicKeyParameter}/{@code AsymmetricPrivateKeyParameter} 拆分）。</p>
 */
public interface Agreement extends Algorithm<AlgorithmFactory<? extends Agreement>> {

    /**
     * 用本地私钥初始化协商。
     *
     * @param params 持有的私钥参数
     */
    void init(AsymmetricPrivateKeyParameter params);

    /**
     * 用对方公钥执行协商，返回共享密钥材料。
     *
     * @param pubKey 对方公钥参数
     * @return 共享密钥字节
     */
    byte[] calculateAgreement(AsymmetricPublicKeyParameter pubKey);
}
