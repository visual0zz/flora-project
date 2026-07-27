package com.flora.crypto.core;

/**
 * 流密码引擎接口。
 * <p>对应常见算法：RC4 / ChaCha20 等。加密与解密对称，可逐字节/逐段处理。</p>
 */
public interface StreamCipher {

    void init(boolean forEncryption, CipherParameters params);

    /** @return 算法名 / transformation */
    String getAlgorithmName();

    /** @return 处理单个字节的结果 */
    byte processByte(byte in);

    /**
     * 处理一段字节。
     *
     * @return 写入 {@code out} 的字节数
     */
    int processBytes(byte[] in, int inOff, int len, byte[] out, int outOff);
}
