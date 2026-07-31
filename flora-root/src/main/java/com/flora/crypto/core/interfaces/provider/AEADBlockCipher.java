package com.flora.crypto.core.interfaces.provider;
import com.flora.crypto.core.interfaces.CipherParameters;

/**
 * 关联数据认证加密（AEAD）接口（Bouncy Castle 风格）。
 * <p>在 {@link BlockCipher} 之外额外暴露「关联数据（AAD）」与「认证标签（MAC）」能力，
 * 代表方案：AES-GCM、AES-CCM、ChaCha20-Poly1305。与把 GCM 折叠进 {@code BlockCipher.process}
 * 不同，这里把 {@code processAADBytes} / {@code getMac} 提升为一等成员，使调用方显式控制
 * 认证流程。</p>
 */
public interface AEADBlockCipher extends AlgorithmFamily {

    void init(boolean forEncryption, CipherParameters params);

    /** @return 算法名 */
    String getAlgorithmName();

    /** @return 处理 {@code len} 字节输入后预计的输出长度（含标签） */
    int getOutputSize(int len);

    /** @return 处理 {@code len} 字节输入后预计的本次增量输出长度（不含标签） */
    int getUpdateOutputSize(int len);

    /** 输入一个关联数据字节（不加密，只参与认证） */
    void processAADByte(byte in);

    /** 输入一段关联数据 */
    void processAADBytes(byte[] in, int inOff, int len);

    /**
     * 处理单个明文/密文字节。
     *
     * @return 写入 {@code out} 的字节数
     */
    int processByte(byte in, byte[] out, int outOff);

    /**
     * 处理一段明文/密文字节。
     *
     * @return 写入 {@code out} 的字节数
     */
    int processBytes(byte[] in, int inOff, int len, byte[] out, int outOff);

    /**
     * 结束处理并产出末段（加密时附带认证标签；解密时校验标签）。
     *
     * @return 写入 {@code out} 的末段字节数
     */
    int doFinal(byte[] out, int outOff);

    /** @return 认证标签字节（解密前应已在校验通过的前提下获取） */
    byte[] getMac();
}
