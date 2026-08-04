package com.flora.crypto.core;
import com.flora.crypto.core.interfaces.provider.Xof;

/**
 * XOF（可变长输出）的最简占位实现。
 * <p>JDK 没有「给我 N 字节输出」的概念槽位，因此默认以占位实现兜底：
 * 除名字外，可变长输出方法一律抛出 {@link UnsupportedOperationException}。
 * 待接入真实引擎（如 SHAKE128/256）后，通过 {@code CryptoProvider.register(Xof.class, ...)} 覆盖即可。</p>
 */
public final class PlaceholderXof implements Xof {

    @Override
    public String getAlgorithmName() {
        return "PLACEHOLDER_XOF";
    }

    @Override
    public int getDigestSize() {
        return 0;
    }

    @Override
    public void update(byte in) {
        // 占位：丢弃输入
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        // 占位：丢弃输入
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        throw new UnsupportedOperationException(
                "PlaceholderXof 为占位实现，请通过 CryptoProvider.register 注册真实 XOF 引擎（如 SHAKE）");
    }

    @Override
    public void reset() {
        // 占位：无状态
    }

    @Override
    public int doFinal(byte[] out, int outOff, int outLen) {
        throw new UnsupportedOperationException(
                "PlaceholderXof 为占位实现，请通过 CryptoProvider.register 注册真实 XOF 引擎（如 SHAKE）");
    }

    @Override
    public int doOutput(byte[] out, int outOff, int outLen) {
        throw new UnsupportedOperationException(
                "PlaceholderXof 为占位实现，请通过 CryptoProvider.register 注册真实 XOF 引擎（如 SHAKE）");
    }
}
