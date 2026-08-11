package com.flora.crypto.newcore.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFactory;

import java.security.SecureRandom;

/**
 * 分组密码填充策略接口。
 * <p>把「填充」从算法中抽离为可组合的策略对象，配合缓冲分组密码包装器使用。
 * 代表实现：PKCS7Padding、ISO7816d4Padding、ZeroBytePadding。</p>
 */
public interface Padding extends Algorithm<AlgorithmFactory<? extends Padding>> {

    /**
     * 初始化（部分填充需要随机数）。
     *
     * @throws IllegalArgumentException 若随机数源不合规
     */
    void init(SecureRandom random) throws IllegalArgumentException;

    /** @return 填充算法名，如 {@code "PKCS7"} */
    String getPaddingName();

    /** @return 块大小 */
    int getBlockSize();

    /**
     * 在 {@code in[inOff..]} 处就地添加填充，使长度达到块对齐。
     *
     * @return 添加的填充字节数
     */
    int addPadding(byte[] in, int inOff);

    /**
     * 计算 {@code in[inOff..]} 末尾的填充字节数（用于解密去填充）。
     *
     * @throws IllegalStateException 若填充非法
     */
    int padCount(byte[] in, int inOff) throws IllegalStateException;

}
