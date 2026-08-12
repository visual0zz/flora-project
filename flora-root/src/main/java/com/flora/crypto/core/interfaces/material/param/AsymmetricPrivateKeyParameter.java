package com.flora.crypto.core.interfaces.material.param;

import com.flora.crypto.core.constant.AsymmetricKeyType;

/**
 * 非对称私钥参数。
 * <p>密钥协商的「本地私钥」、解封装的「接收方私钥」、签名的「私钥」等场景应声明本类型，
 * 从而在编译期排除传入公钥的可能。</p>
 * <p>{@link #getPrivateKey()} 仅返回核心字节材料，其含义（曲线 / 域 / 协议族）须由
 * {@link #getKeyKind()} 给出，二者配合才能让裸字节可解析。</p>
 */
public interface AsymmetricPrivateKeyParameter extends CipherParameter {
    byte[] getPrivateKey();

    /** @return 本私钥所属的非对称密钥种类 */
    AsymmetricKeyType getKeyKind();
}
