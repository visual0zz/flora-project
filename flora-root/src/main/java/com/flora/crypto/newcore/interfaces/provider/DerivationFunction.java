package com.flora.crypto.newcore.interfaces.provider;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFamily;
import com.flora.crypto.newcore.interfaces.param.DerivationParameters;

/**
 * 密钥派生函数（KDF / 口令哈希）接口。
 * <p>对应常见方案：HKDF、KDF1/2、scrypt、bcrypt、Argon2 等。</p>
 */
public interface DerivationFunction extends Algorithm<AlgorithmFamily<? extends DerivationFunction>> {

    void init(DerivationParameters params);

    void update(byte[] in, int inOff, int len);

    /**
     * 派生密钥材料并写入 {@code out}。
     *
     * @param out    输出缓冲区
     * @param outOff 起始偏移
     * @param len    期望派生长度（字节）
     * @return 实际写入的字节数
     */
    int generateBytes(byte[] out, int outOff, int len);
}
