package com.flora.crypto.newcore;

/**
 * 算法分类枚举。
 * <p>仅做顶层分类与展示（中文描述），所属分类由 {@link com.flora.crypto.newcore.interfaces.AlgorithmFactory#category()}
 * 反向声明。每个分类对应 {@code interfaces/provider} 下一个角色接口。</p>
 */
public enum AlgorithmCategory {
    DIGEST("摘要算法"),
    MAC("消息认证码"),
    BLOCK_CIPHER("分组密码"),
    ASYMMETRIC_BLOCK_CIPHER("非对称分组密码"),
    ASYMMETRIC_CIPHER("流式非对称密码"),
    ASYMMETRIC_KEY_PAIR_GENERATOR("非对称密钥对生成器"),
    AGREEMENT("密钥协商"),
    KEM("密钥封装机制"),
    SIGNATURE("数字签名"),
    DERIVATION("密钥派生 / 口令哈希"),
    BLOCK_CIPHER_PADDING("分组密码填充"),
    ENTROPY_SOURCE("熵源"),
    DRBG("确定性随机比特生成器"),
    ;

    private final String desc;

    AlgorithmCategory(String desc) {
        this.desc = desc;
    }

    /** @return 分类中文描述 */
    public String desc() {
        return desc;
    }
}
