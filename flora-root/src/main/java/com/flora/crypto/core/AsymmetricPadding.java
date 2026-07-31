package com.flora.crypto.core;

/**
 * 非对称填充策略（自研组合层）。
 * <p>包装在裸 RSA 原语之上：加密方向将明文填充到密钥长度，解密方向去除填充还原明文。
 * 代表方案：PKCS1v1.5、OAEP。与 {@link BlockCipherPadding}（对称填充）对称。</p>
 */
public interface AsymmetricPadding {

    /** @return 填充名称 */
    String getPaddingName();

    /**
     * @param keyBytes 密钥长度（字节）
     * @return 该填充下可容纳的最大明文长度（字节）
     */
    int getInputBlockSize(int keyBytes);

    /**
     * 加密方向：把明文填充为 {@code keyBytes} 长。
     *
     * @return 填充后的整块数据（长度 = keyBytes）
     */
    byte[] pad(byte[] in, int inOff, int inLen, int keyBytes);

    /**
     * 解密方向：去除填充还原明文。
     *
     * @return 原始明文
     * @throws IllegalArgumentException 填充格式非法
     */
    byte[] unpad(byte[] in) throws IllegalArgumentException;
}
