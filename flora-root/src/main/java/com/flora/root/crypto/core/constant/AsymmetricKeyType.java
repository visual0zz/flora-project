package com.flora.root.crypto.core.constant;

/**
 * 非对称公私钥种类。
 * <p>描述密钥对底层的数学族/协议族，与 {@code AsymmetricPublicKeyParameter} /
 * {@code AsymmetricPrivateKeyParameter}（仅承载核心字节材料）分层：本枚举回答
 * 「这段字节属于哪类密钥」，参数对象回答「密钥材料是什么」。两者配合才能让裸字节可解析。</p>
 * <p>覆盖常见协商、签名、封装算法所需的公私钥形态；新增算法时在底部追加常量即可。</p>
 */
public enum AsymmetricKeyType {

    /** RSA 密钥对（签名 / 加密，模幂类）。 */
    RSA,

    /** 有限域 Diffie-Hellman（DH）密钥对，基于素数域离散对数。 */
    DH,

    /** 传统椭圆曲线（secp / NIST 等）密钥对，用于 ECDH / ECDSA。 */
    EC,

    /** Curve25519 / Curve448 类密钥对，用于 X25519 / X448 协商与 Ed25519 / Ed448 签名。 */
    CURVE25519,

    /** 国密 SM2 椭圆曲线密钥对（签名 / 协商）。 */
    SM2,

    /** 后量子密钥封装（ML-KEM / CRYSTALS-Kyber 等）公钥与私钥。 */
    ML_KEM,

    /** 后量子签名（ML-DSA / SLH-DSA / Dilithium / Sphincs+ 等）公钥与私钥。 */
    PQ_SIGNATURE,

    /** 未归类的原始/自定义非对称密钥（仅持有字节，解析依赖调用方上下文）。 */
    RAW;
}
