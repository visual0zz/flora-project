package com.flora.crypto.core;
import com.flora.crypto.core.interfaces.DerivationParameters;
import com.flora.crypto.core.interfaces.provider.Mac;
import com.flora.crypto.core.interfaces.provider.MacDerivationFunction;
import com.flora.crypto.core.param.HkdfParameters;
import com.flora.crypto.core.param.KeyParameter;

import com.flora.java.CheckUtil;

/**
 * HKDF-Expand（RFC 5869）派生函数，纯 Java 实现，不依赖 JDK。
 * <p>以任意 {@link Mac}（通常 HMAC）为原语：T(0)=空，T(i)=HMAC(PRK, T(i-1) || info || i)，
 * 循环拼接直至得到所需长度。</p>
 */
public final class HkdfDerivationFunction implements MacDerivationFunction {

    private final Mac mac;
    private final int macSize;
    private byte[] prk;
    private byte[] info;

    public HkdfDerivationFunction(Mac mac) {
        CheckUtil.notNull(mac, "MAC 引擎不能为空");
        this.mac = mac;
        this.macSize = mac.getMacSize();
    }

    @Override
    public String getAlgorithmName() {
        return "HKDF";
    }

    @Override
    public void init(DerivationParameters params) {
        CheckUtil.notNull(params, "参数不能为空");
        if (!(params instanceof HkdfParameters)) {
            throw new IllegalArgumentException("HKDF 需要 HkdfParameters");
        }
        HkdfParameters p = (HkdfParameters) params;
        this.prk = p.getKey();
        this.info = p.getInfo();
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        if (info == null) {
            info = new byte[len];
            System.arraycopy(in, inOff, info, 0, len);
        } else {
            byte[] merged = new byte[info.length + len];
            System.arraycopy(info, 0, merged, 0, info.length);
            System.arraycopy(in, inOff, merged, info.length, len);
            info = merged;
        }
    }

    @Override
    public int generateBytes(byte[] out, int outOff, int len) {
        CheckUtil.notNull(out, "输出缓冲区不能为空");
        CheckUtil.mustTrue(len > 0, "派生长度必须大于 0");
        byte[] t = new byte[0];
        int counter = 1;
        int written = 0;
        while (written < len) {
            mac.init(new KeyParameter(prk));
            mac.update(t, 0, t.length);
            if (info != null) {
                mac.update(info, 0, info.length);
            }
            mac.update((byte) counter);
            byte[] block = new byte[macSize];
            mac.doFinal(block, 0);
            int toCopy = Math.min(macSize, len - written);
            System.arraycopy(block, 0, out, outOff + written, toCopy);
            written += toCopy;
            t = block;
            counter++;
        }
        return len;
    }

    @Override
    public Mac getMac() {
        return mac;
    }
}
