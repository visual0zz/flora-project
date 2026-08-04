package com.flora.crypto.core.combinator;
import com.flora.crypto.core.AsymmetricPadding;
import com.flora.crypto.core.interfaces.provider.AsymmetricBlockCipher;
import com.flora.crypto.core.interfaces.CipherParameters;

import com.flora.java.CheckUtil;

/**
 * 非对称填充组合器（自研组合层）。
 * <p>把裸 RSA 原语（{@link AsymmetricBlockCipher}，如 {@code JdkAsymmetricBlockCipher("RSA")}）
 * 与填充策略（{@link AsymmetricPadding}，如 PKCS1v1.5/OAEP）组合为完整的非对称加解密。
 * 加密方向先填充后裸运算，解密方向先裸运算后去填充。</p>
 */
public final class PaddedAsymmetricBlockCipher implements AsymmetricBlockCipher {

    private final AsymmetricBlockCipher engine;
    private final AsymmetricPadding padding;
    private boolean encrypting;
    private int keyBytes;

    public PaddedAsymmetricBlockCipher(AsymmetricBlockCipher engine, AsymmetricPadding padding) {
        CheckUtil.notNull(engine, "底层非对称引擎不能为空");
        CheckUtil.notNull(padding, "填充不能为空");
        this.engine = engine;
        this.padding = padding;
    }

    @Override
    public void init(boolean forEncryption, CipherParameters params) {
        this.encrypting = forEncryption;
        engine.init(forEncryption, params);
        this.keyBytes = engine.getOutputBlockSize();
    }

    @Override
    public String getAlgorithmName() {
        return engine.getAlgorithmName() + "/" + padding.getPaddingName();
    }

    @Override
    public int getInputBlockSize() {
        return encrypting ? padding.getInputBlockSize(keyBytes) : keyBytes;
    }

    @Override
    public int getOutputBlockSize() {
        return keyBytes;
    }

    @Override
    public byte[] processBlock(byte[] in, int inOff, int len) {
        if (encrypting) {
            byte[] padded = padding.pad(in, inOff, len, keyBytes);
            return engine.processBlock(padded, 0, padded.length);
        }
        byte[] raw = engine.processBlock(in, inOff, len);
        return padding.unpad(raw);
    }
}
