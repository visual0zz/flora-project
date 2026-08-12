package com.flora.crypto.core.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.crypto.core.interfaces.material.param.CipherParameter;
import com.flora.crypto.core.interfaces.material.param.ParameterWithIV;

/**
 * 分组密码引擎接口。
 * <p>一次处理一个固定大小的块。对应常见算法：AES / SM4 / Blowfish 等。
 * 分组密码模式（CBC / CFB / OFB / CTR / GCM / SIC）与填充不作为 transformation 字符串，
 * 而是各自实现本接口的独立 {@code BlockCipher}（见 {@code mode/} 组合器与 {@code padding/} 策略），
 * 通过 {@code AlgorithmFactory.componentTypes()} 声明依赖的底层分组密码后按需注入组合。</p>
 */
public interface BlockCipher extends Algorithm<AlgorithmFactory<? extends BlockCipher>> {

    /**
     * 初始化。
     *
     * @param forEncryption 是否为加密方向
     * @param params        参数（对称密钥，GCM/CBC 等还需 {@link ParameterWithIV}）
     */
    void init(boolean forEncryption,CipherParameter params);

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
