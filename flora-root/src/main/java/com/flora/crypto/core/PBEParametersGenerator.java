package com.flora.crypto.core;

import com.flora.java.CheckUtil;

/**
 * 基于口令的参数生成器基类（Bouncy Castle 风格）。
 * <p>把「口令 + salt + 迭代次数」派生为算法所需的 {@link CipherParameters}（密钥，或密钥 + MAC 密钥）。
 * JDK 仅原生支持 PBKDF2（经 {@code SecretKeyFactory}），由 {@code JdkPBEParametersGenerator} 适配；
 * scrypt / bcrypt / Argon2 等需自定义实现后接入。</p>
 */
public abstract class PBEParametersGenerator {

    protected byte[] password;
    protected byte[] salt;
    protected int iterationCount;

    /**
     * 初始化派生输入。
     *
     * @param password       口令（原始字节，注意生命周期后及时清零）
     * @param salt           盐值
     * @param iterationCount 迭代次数
     */
    public void init(byte[] password, byte[] salt, int iterationCount) {
        CheckUtil.notNull(password, "口令不能为空");
        CheckUtil.notNull(salt, "盐值不能为空");
        this.password = password.clone();
        this.salt = salt.clone();
        this.iterationCount = iterationCount;
    }

    public byte[] getPassword() {
        return password.clone();
    }

    public byte[] getSalt() {
        return salt.clone();
    }

    public int getIterationCount() {
        return iterationCount;
    }

    /** @return 派生出的密钥参数（{@code keySizeBits} 为密钥位数） */
    public abstract CipherParameters generateDerivedParameters(int keySizeBits);

    /** @return 派生出的 MAC 密钥参数（{@code keySizeBits} 为密钥位数） */
    public abstract CipherParameters generateDerivedMacParameters(int keySizeBits);
}
