package com.flora.crypto.core;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.crypto.core.interfaces.Decapsulator;
import com.flora.crypto.core.interfaces.Encapsulator;
import com.flora.crypto.core.interfaces.provider.KeyEncapsulationMechanism;

/**
 * KEM 占位实现（对应 JDK/本项目尚不支持的算法，如后量子 ML-KEM）。
 * <p>封装/解封装一律抛 {@link UnsupportedOperationException}。真实引擎（如 ML-KEM 格密码）
 * 实现后通过 {@code CryptoProvider.register(KeyEncapsulationMechanism.class, ...)} 覆盖即可。</p>
 */
public final class PlaceholderKem implements KeyEncapsulationMechanism {

    @Override
    public String getAlgorithmName() {
        return "placeholder";
    }

    @Override
    public Encapsulator newEncapsulator(CipherParameters publicKey) {
        throw new UnsupportedOperationException(
                "PlaceholderKem 为占位实现，请通过 CryptoProvider.register 注册真实 KEM 引擎（如 ML-KEM）");
    }

    @Override
    public Decapsulator newDecapsulator(CipherParameters privateKey) {
        throw new UnsupportedOperationException(
                "PlaceholderKem 为占位实现，请通过 CryptoProvider.register 注册真实 KEM 引擎（如 ML-KEM）");
    }
}
