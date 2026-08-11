package com.flora.crypto.newcore.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFamily;

/**
 * 摘要（散列）引擎接口。
 * <p>对应常见算法：SHA-256 / SHA-512 / MD5 等。仅暴露每族真正共有的操作：update / doFinal / reset。</p>
 */
public interface Digest extends Algorithm<AlgorithmFamily<? extends Digest>> {

    /** @return 输出摘要的字节长度 */
    int getDigestResultSize();
    /**
     * @return 摘要算法的内部块长度（字节），如 SHA-256 为 64、SHA-512 为 128
     */
    int getInternalBlockLength();

    void update(byte in);

    void update(byte[] in, int inOff, int len);

    /**
     * 完成摘要计算并写入 {@code out}。
     *
     * @param out    输出缓冲区
     * @param outOff 起始偏移
     * @return 写入的字节数（即摘要长度）
     */
    int doFinal(byte[] out, int outOff);

    void reset();
}
