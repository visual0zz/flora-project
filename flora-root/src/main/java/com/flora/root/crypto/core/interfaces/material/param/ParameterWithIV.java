package com.flora.root.crypto.core.interfaces.material.param;

/**
 * 携带 IV 的密码参数（如 CBC/GCM/CTR 模式需要的初始化向量）。
 */
public interface ParameterWithIV extends CipherParameter {

    CipherParameter getParameters();

    byte[] getIV();
}
