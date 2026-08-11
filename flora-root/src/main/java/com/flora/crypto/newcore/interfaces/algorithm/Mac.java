package com.flora.crypto.newcore.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameter;

/**
 * MAC（消息认证码）引擎接口。
 * <p>对应常见算法：HmacSHA256 / HmacSHA1 / GMac 等。与 {@link Digest} 类似，但需密钥初始化。</p>
 */
public interface Mac extends Algorithm<AlgorithmFactory<? extends Mac>> {

    void init(CipherParameter params);

    /** @return MAC 字节长度 */
    int getMacSize();

    void update(byte in);

    void update(byte[] in, int inOff, int len);

    /**
     * 完成计算并写入 {@code out}。
     *
     * @param out    输出缓冲区
     * @param outOff 起始偏移
     * @return 写入的字节数（即 MAC 长度）
     */
    int doFinal(byte[] out, int outOff);

    void reset();
}
