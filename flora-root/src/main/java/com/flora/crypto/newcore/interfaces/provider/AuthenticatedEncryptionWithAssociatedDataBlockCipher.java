package com.flora.crypto.newcore.interfaces.provider;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFamily;
import com.flora.crypto.newcore.interfaces.param.CipherParameters;

/**
 * 带关联数据的认证加密分组密码（AEAD）接口。
 * <p>在 {@link BlockCipher} 基础上增加认证标签与附加关联数据（AAD），对应 GCM / CCM / ChaCha20-Poly1305 等。
 * 加密时产出密文 + 认证标签，解密时校验标签以检测篡改。</p>
 */
public interface AuthenticatedEncryptionWithAssociatedDataBlockCipher
        extends Algorithm<AlgorithmFamily<? extends AuthenticatedEncryptionWithAssociatedDataBlockCipher>> {

    void init(boolean forEncryption, CipherParameters params);

    /** @return 算法名 / transformation */
    String getAlgorithmName();

    /** @return 底层块大小（字节） */
    int getBlockSize();

    /**
     * 处理一个块（同 {@link BlockCipher#processBlock}）。
     */
    int processBlock(byte[] in, int inOff, byte[] out, int outOff);

    /**
     * 便捷入口：一次性处理整段数据（含认证标签）。
     *
     * @param data 明文或密文
     * @return 密文或明文
     */
    byte[] process(byte[] data);

    /**
     * 关联数据：参与认证但不加密，用于上下文绑定（如包头、协议版本）。
     *
     * @param assocText 关联数据
     * @param off       起始偏移
     * @param len       长度
     */
    void processAADBytes(byte[] assocText, int off, int len);

    /**
     * 完成计算并产出（密文 + 认证标签）或校验标签（解密）。
     *
     * @param out    输出缓冲区
     * @param outOff 起始偏移
     * @return 写入的字节数
     */
    int doFinal(byte[] out, int outOff);

    /** @return 认证标签字节长度 */
    int getMacSize();
}
