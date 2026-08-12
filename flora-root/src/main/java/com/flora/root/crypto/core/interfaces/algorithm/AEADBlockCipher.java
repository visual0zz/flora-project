package com.flora.root.crypto.core.interfaces.algorithm;

import com.flora.root.common.register.Algorithm;
import com.flora.root.common.register.AlgorithmFactory;
import com.flora.root.crypto.core.interfaces.material.param.CipherParameter;
import com.flora.root.crypto.core.interfaces.material.param.ParameterWithIV;

/**
 * 带关联数据的认证加密分组密码（AEAD）接口。
 * <p>与 {@link LinkedBlockCipher} 同属模式层，但遵循自身自然的算法形态，不强求与链式模式一致：
 * AAD 以 {@link #processAADBytes(byte[])} 流式喂入（可参与认证但不加密），
 * 主数据以整段 {@link #process(byte[])} 一次性消费，末尾 {@link #doFinal()} 收尾。
 * AEAD 在加密之外额外产出认证标签，并支持仅参与认证的关联数据（AAD），对应 GCM / CCM / ChaCha20-Poly1305 等。</p>
 * <p>加密时 {@link #process(byte[])} 产出「密文 ‖ 认证标签」，解密时产出明文并校验标签以检测篡改。
 * 解密明文与认证标签由于密码学约束必须递延到 {@link #doFinal()} 才能交付，因此主数据采用整段而非逐块流式。</p>
 */
public interface AEADBlockCipher
        extends Algorithm<AlgorithmFactory<? extends AEADBlockCipher>> {

    /**
     * 初始化模式。
     *
     * @param forEncryption 是否为加密方向
     * @param params        参数（对称密钥；GCM 等还需 {@link ParameterWithIV}）
     */
    void init(boolean forEncryption, CipherParameter params);

    /** @return 算法名 / transformation，如 {@code "AES/GCM"} */
    String getAlgorithmName();

    /** @return 底层块大小（字节） */
    int getBlockSize();

    /**
     * 关联数据：参与认证但不加密，用于上下文绑定（如包头、协议版本）。
     *
     * @param assocText 关联数据
     */
    void processAADBytes(byte[] assocText);

    /**
     * 关联数据：处理 {@code assocText[off..off+len]}。
     *
     * @param assocText 关联数据
     * @param off       起始偏移
     * @param len       长度
     */
    void processAADBytes(byte[] assocText, int off, int len);

    /**
     * 处理整段主数据：加密返回「密文 ‖ 认证标签」，解密返回明文（并校验标签）。
     * <p>可多次调用（每段均视为连续主数据），但解密时明文与标签必须递延到 {@link #doFinal()} 才交付。</p>
     *
     * @param data 明文或密文（解密时含认证标签）
     * @return 加密时返回「密文 ‖ 认证标签」，解密时不立即返回明文（返回空，明文在 {@link #doFinal()}）
     */
    byte[] process(byte[] data);

    /**
     * 分步输入的收尾：在已通过 {@link #processAADBytes} 喂入关联数据、通过 {@link #process(byte[])}
     * 喂入主数据后，产出「密文 ‖ 认证标签」（加密）或校验标签并产出明文（解密）。
     *
     * @return 密文（含标签）或明文
     */
    byte[] doFinal();

    /** @return 认证标签字节长度 */
    int getMacSize();
}