package com.flora.crypto.core;

import java.security.SecureRandom;

/**
 * 分组密码填充策略接口（Bouncy Castle 风格）。
 * <p>把「填充」从算法中抽离为可组合的策略对象，配合 {@code PaddedBufferedBlockCipher} 使用。
 * 代表实现：{@code PKCS7Padding}、{@code ISO7816d4Padding}、{@code ZeroBytePadding}。
 * 这是 BC「对象组合」风格的典型——模式、填充都作为可叠加的包装器，而非写死在算法字符串里。</p>
 */
public interface BlockCipherPadding {

    /**
     * 初始化（部分填充需要随机数）。
     *
     * @throws IllegalArgumentException 若随机数源不合规
     */
    void init(SecureRandom random) throws IllegalArgumentException;

    /** @return 填充算法名，如 {@code "PKCS7"} */
    String getPaddingName();

    /**
     * 在 {@code in[inOff..]} 处就地添加填充，使长度达到块对齐。
     *
     * @return 添加的填充字节数
     */
    int addPadding(byte[] in, int inOff);

    /**
     * 计算 {@code in} 末尾的填充字节数（用于解密去填充）。
     *
     * @throws IllegalStateException 若填充非法
     */
    int padCount(byte[] in) throws IllegalStateException;

    /** @return 该填充的固定长度（变长填充返回 0） */
    int getPaddingSize();
}
