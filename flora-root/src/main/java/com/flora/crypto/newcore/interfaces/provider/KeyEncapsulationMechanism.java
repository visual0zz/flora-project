package com.flora.crypto.newcore.interfaces.provider;

import com.flora.crypto.newcore.interfaces.Algorithm;
import com.flora.crypto.newcore.interfaces.AlgorithmFactory;
import com.flora.crypto.newcore.interfaces.param.CipherParameters;
import com.flora.crypto.newcore.interfaces.provider.kem.Encapsulator;
import com.flora.crypto.newcore.interfaces.provider.kem.Decapsulator;

/**
 * 密钥封装机制（Key Encapsulation Mechanism）接口。
 * <p>现代密钥交换范式：发送方用接收方公钥「封装」出一个临时对称密钥与一段密文（encapsulation），
 * 接收方用私钥「解封装」还原出同一个对称密钥。对称密钥从不下发明文，天然适配后量子算法（ML-KEM）。</p>
 */
public interface KeyEncapsulationMechanism extends Algorithm<AlgorithmFactory<? extends KeyEncapsulationMechanism>> {

    /**
     * 创建封装器（发送方用）。
     *
     * @param publicKey 接收方公钥参数
     */
    Encapsulator newEncapsulator(CipherParameters publicKey);

    /**
     * 创建解封装器（接收方用）。
     *
     * @param privateKey 接收方私钥参数
     */
    Decapsulator newDecapsulator(CipherParameters privateKey);
}
