package com.flora.crypto.newcore.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFamily;
import com.flora.crypto.newcore.interfaces.material.param.BlockCipherParameter;
import com.flora.crypto.newcore.interfaces.material.param.ParameterWithIV;

/**
 * 分组密码引擎接口。
 * <p>一次处理一个固定大小的块。对应常见算法：AES / SM4 / Blowfish 等。
 * 模式（CBC/CTR/GCM）与填充由具体适配器通过 transformation 字符串表达，而非放在该接口里。</p>
 */
public interface BlockCipher extends Algorithm<AlgorithmFamily<? extends BlockCipher>> {

    /**
     * 初始化。
     *
     * @param forEncryption 是否为加密方向
     * @param params        参数（对称密钥，GCM/CBC 等还需 {@link ParameterWithIV}）
     */
    void init(boolean forEncryption,BlockCipherParameter params);

    /** @return 块大小（字节） */
    int getBlockSize();


    /**
     * 处理一个块。
     *
     * @param in    输入
     * @param inOff 输入偏移
     * @param out   输出
     * @param outOff 输出偏移
     * @return 写入的字节数
     */
    int processBlock(byte[] in, int inOff, byte[] out, int outOff);

}
