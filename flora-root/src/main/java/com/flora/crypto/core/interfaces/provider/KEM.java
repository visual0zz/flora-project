package com.flora.crypto.core.interfaces.provider;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.crypto.core.interfaces.Decapsulator;
import com.flora.crypto.core.interfaces.Encapsulator;

/**
 * 密钥封装机制（Key Encapsulation Mechanism）接口（Bouncy Castle 风格）。
 * <p>现代密钥交换范式：发送方用接收方公钥「封装」出一个临时对称密钥与一段密文（encapsulation），
 * 接收方用私钥「解封装」还原出同一个对称密钥。对称密钥从不下发明文，天然适配后量子算法（ML-KEM）。</p>
 * <p>本项目的默认实现 {@code AgreementBasedKem} 用「密钥协商 + KDF」构造（ECDH / X25519 等经典曲线）；
 * 后量子算法（ML-KEM）需真实格密码引擎，未实现时走 {@code PlaceholderKem} 占位。</p>
 */
public interface KEM extends AlgorithmFamily {

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
