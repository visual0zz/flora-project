package com.flora.crypto.core.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.crypto.core.interfaces.material.param.AsymmetricPrivateKeyParameter;
import com.flora.crypto.core.interfaces.material.param.AsymmetricPublicKeyParameter;
import com.flora.crypto.core.interfaces.material.kem.Decapsulator;
import com.flora.crypto.core.interfaces.material.kem.Encapsulator;

/**
 * 密钥封装机制（Key Encapsulation Mechanism）接口。
 * <p>现代密钥交换范式：发送方用接收方公钥「封装」出一个临时对称密钥与一段密文（encapsulation），
 * 接收方用私钥「解封装」还原出同一个对称密钥。对称密钥从不下发明文，天然适配后量子算法（ML-KEM）。</p>
 * <p>封装收公钥参数、解封装收私钥参数，公钥/私钥类型在签名层即区分（同 {@link Agreement}）。</p>
 */
public interface KeyEncapsulationMechanism extends Algorithm<AlgorithmFactory<? extends KeyEncapsulationMechanism>> {

    /**
     * 创建封装器（发送方用）。
     *
     * @param publicKey 接收方公钥参数
     */
    Encapsulator newEncapsulator(AsymmetricPublicKeyParameter publicKey);

    /**
     * 创建解封装器（接收方用）。
     *
     * @param privateKey 接收方私钥参数
     */
    Decapsulator newDecapsulator(AsymmetricPrivateKeyParameter privateKey);
}
