package com.flora.crypto.core.mode;
import com.flora.tag.ThreadFragile;

import com.flora.crypto.core.AEADBlockCipher;
import com.flora.crypto.core.BlockCipher;
import com.flora.crypto.core.CipherParameters;
import com.flora.crypto.core.engine.JdkAeadBlockCipher;

/**
 * AES-GCM 组合风格封装（Bouncy Castle 风格）。
 * <p>包裹一个原始分组密码（如 {@code AES/ECB/NoPadding}），按 BC「组合」约定对外呈现为
 * {@link AEADBlockCipher}。内部 GCM 认证逻辑委托给 JDK（GHASH 自实现风险较高，故走 JDK 适配器），
 * 但接口面与 BC 的 {@code GCMBlockCipher} 一致。</p>
 */
@ThreadFragile
public final class GCMBlockCipher implements AEADBlockCipher {

    private final JdkAeadBlockCipher delegate;

    public GCMBlockCipher(BlockCipher raw) {
        String base = baseName(raw.getAlgorithmName());
        this.delegate = JdkAeadBlockCipher.of(base + "/GCM/NoPadding");
    }

    private static String baseName(String name) {
        int i = name.indexOf('/');
        return i < 0 ? name : name.substring(0, i);
    }

    @Override
    public void init(boolean forEncryption, CipherParameters params) {
        delegate.init(forEncryption, params);
    }

    @Override
    public String getAlgorithmName() {
        return delegate.getAlgorithmName();
    }

    @Override
    public int getOutputSize(int len) {
        return delegate.getOutputSize(len);
    }

    @Override
    public int getUpdateOutputSize(int len) {
        return delegate.getUpdateOutputSize(len);
    }

    @Override
    public void processAADByte(byte in) {
        delegate.processAADByte(in);
    }

    @Override
    public void processAADBytes(byte[] in, int inOff, int len) {
        delegate.processAADBytes(in, inOff, len);
    }

    @Override
    public int processByte(byte in, byte[] out, int outOff) {
        return delegate.processByte(in, out, outOff);
    }

    @Override
    public int processBytes(byte[] in, int inOff, int len, byte[] out, int outOff) {
        return delegate.processBytes(in, inOff, len, out, outOff);
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        return delegate.doFinal(out, outOff);
    }

    @Override
    public byte[] getMac() {
        return delegate.getMac();
    }
}
