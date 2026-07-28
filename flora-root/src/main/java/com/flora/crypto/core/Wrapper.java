package com.flora.crypto.core;

/**
 * 密钥包装（Key Wrap）接口（Bouncy Castle 风格）。
 * <p>对应常见方案：AESWrap / AESWrapPad（AES 密钥包装）、RSA 密钥包装等。
 * 与把密钥当普通明文加密不同，密钥包装通常走 {@code Cipher.WRAP_MODE}/{@code UNWRAP_MODE}
 * 这种带格式保证的路径。JDK 已具备该能力，由 {@code JdkWrapper} 适配。</p>
 */
public interface Wrapper {

    /**
     * 初始化。
     *
     * @param forWrapping 是否为包装（true）还是解包（false）
     * @param params      包装密钥参数
     */
    void init(boolean forWrapping, CipherParameters params);

    /** @return 算法名 */
    String getAlgorithmName();

    /** @return 包装后的密钥字节 */
    byte[] wrap(byte[] in, int inOff, int len);

    /** @return 解包后的密钥字节 */
    byte[] unwrap(byte[] in, int inOff, int len);
}
