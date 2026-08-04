package com.flora.crypto.core;
import com.flora.crypto.core.interfaces.DerivationParameters;
import com.flora.crypto.core.interfaces.provider.Digest;
import com.flora.crypto.core.interfaces.provider.DigestDerivationFunction;
import com.flora.crypto.core.param.KdfParameters;

import com.flora.java.CheckUtil;

/**
 * KDF2（ISO 18033-2）派生函数，纯 Java 实现，不依赖 JDK/BC。
 * <p>以任意 {@link Digest} 为原语：K(i) = HASH(Z || Counter(4 字节大端) [|| sharedInfo])，
 * 计数器从 1 开始，循环拼接直至得到所需长度。</p>
 */
public final class Kdf2DerivationFunction implements DigestDerivationFunction {

    private final Digest digest;
    private final int hLen;
    private byte[] shared;
    private byte[] sharedInfo;

    public Kdf2DerivationFunction(Digest digest) {
        CheckUtil.notNull(digest, "摘要引擎不能为空");
        this.digest = digest;
        this.hLen = digest.getDigestSize();
    }

    @Override
    public String getAlgorithmName() {
        return "KDF2";
    }

    @Override
    public void init(DerivationParameters params) {
        CheckUtil.notNull(params, "参数不能为空");
        if (!(params instanceof KdfParameters)) {
            throw new IllegalArgumentException("KDF2 需要 KdfParameters");
        }
        KdfParameters p = (KdfParameters) params;
        this.shared = p.getShared();
        this.sharedInfo = p.getIV();
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        // KDF2 的共享信息在 init 时确定；此处允许追加更多信息
        if (sharedInfo == null) {
            sharedInfo = new byte[len];
            System.arraycopy(in, inOff, sharedInfo, 0, len);
        } else {
            byte[] merged = new byte[sharedInfo.length + len];
            System.arraycopy(sharedInfo, 0, merged, 0, sharedInfo.length);
            System.arraycopy(in, inOff, merged, sharedInfo.length, len);
            sharedInfo = merged;
        }
    }

    @Override
    public int generateBytes(byte[] out, int outOff, int len) {
        CheckUtil.notNull(out, "输出缓冲区不能为空");
        CheckUtil.mustTrue(len > 0, "派生长度必须大于 0");
        byte[] k = new byte[hLen];
        int counter = 1;
        int written = 0;
        while (written < len) {
            digest.reset();
            digest.update(shared, 0, shared.length);
            digest.update((byte) (counter >>> 24));
            digest.update((byte) (counter >>> 16));
            digest.update((byte) (counter >>> 8));
            digest.update((byte) counter);
            if (sharedInfo != null) {
                digest.update(sharedInfo, 0, sharedInfo.length);
            }
            digest.doFinal(k, 0);
            int toCopy = Math.min(hLen, len - written);
            System.arraycopy(k, 0, out, outOff + written, toCopy);
            written += toCopy;
            counter++;
        }
        return len;
    }

    @Override
    public Digest getDigest() {
        return digest;
    }
}
