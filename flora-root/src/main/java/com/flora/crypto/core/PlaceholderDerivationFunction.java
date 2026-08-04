package com.flora.crypto.core;
import com.flora.crypto.core.interfaces.provider.DerivationFunction;
import com.flora.crypto.core.interfaces.DerivationParameters;

/**
 * 派生函数（KDF）的最简占位实现。
 * <p>JCA 没有第一等的 KDF 抽象（仅 {@code SecretKeyFactory} 的 PBKDF2），故默认以占位兜底：
 * {@code generateBytes} 抛 {@link UnsupportedOperationException}。项目已随附两个纯 Java 真实实现
 * （{@code Kdf2DerivationFunction} / {@code HkdfDerivationFunction}），可按名注册后使用。</p>
 */
public final class PlaceholderDerivationFunction implements DerivationFunction {

    @Override
    public String getAlgorithmName() {
        return "placeholder";
    }

    @Override
    public void init(DerivationParameters params) {
        // 占位：不保存参数
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        // 占位：丢弃输入
    }

    @Override
    public int generateBytes(byte[] out, int outOff, int len) {
        throw new UnsupportedOperationException(
                "PlaceholderDerivationFunction 为占位实现，请通过 CryptoProvider.register 注册真实 KDF（如 KDF2 / HKDF）");
    }
}
