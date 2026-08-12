package com.flora.root.crypto.core.interfaces.material.param;

import com.flora.root.crypto.core.constant.AsymmetricKeyType;

/**
 * 非对称公钥参数。
 * <p>密钥协商的「对方公钥」、封装的「接收方公钥」、验签的「公钥」等场景应声明本类型，
 * 从而在编译期排除传入私钥的可能。</p>
 * <p>{@link #getPublicKey()} 仅返回核心字节材料，其含义（曲线 / 域 / 协议族）须由
 * {@link #getKeyKind()} 给出，二者配合才能让裸字节可解析。</p>
 */
public interface AsymmetricPublicKeyParameter extends CipherParameter {
    byte[] getPublicKey();

    /** @return 本公钥所属的非对称密钥种类 */
    AsymmetricKeyType getKeyKind();
}
