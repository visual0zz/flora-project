package com.flora.crypto.core.impl;

import com.flora.crypto.core.interfaces.DerivationParameters;
import com.flora.crypto.core.interfaces.provider.Mac;
import com.flora.crypto.core.interfaces.provider.MacDerivationFunction;
import com.flora.crypto.core.param.KeyParameter;
import com.flora.crypto.core.param.Pbkdf2Parameters;

import com.flora.java.CheckUtil;

/**
 * PBKDF2 口令派生函数（RFC 8018 §5.2），纯 Java 实现，不依赖 JDK/BC。
 * <p>以任意 {@link Mac}（通常 HMAC）为 PRF 迭代：
 * {@code DK = T1 ‖ T2 ‖ ... ‖ Tn}，
 * {@code Ti = U1 ⊕ U2 ⊕ ... ⊕ Uc}，
 * {@code U1 = PRF(P, S ‖ INT32_BE(i))}，
 * {@code Uj = PRF(P, U(j-1))}。</p>
 */
public final class Pbkdf2DerivationFunction implements MacDerivationFunction {

    private final Mac mac;
    private byte[] password;
    private byte[] salt;
    private int iterationCount;

    public Pbkdf2DerivationFunction(Mac mac) {
        CheckUtil.notNull(mac, "PRF 引擎不能为空");
        this.mac = mac;
    }

    @Override
    public String getAlgorithmName() {
        return "PBKDF2";
    }

    @Override
    public void init(DerivationParameters params) {
        CheckUtil.notNull(params, "参数不能为空");
        if (!(params instanceof Pbkdf2Parameters)) {
            throw new IllegalArgumentException("PBKDF2 需要 Pbkdf2Parameters");
        }
        Pbkdf2Parameters p = (Pbkdf2Parameters) params;
        this.password = p.getPassword();
        this.salt = p.getSalt();
        this.iterationCount = p.getIterationCount();
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        throw new UnsupportedOperationException("PBKDF2 为非增量 KDF，不支持增量输入");
    }

    @Override
    public int generateBytes(byte[] out, int outOff, int len) {
        CheckUtil.notNull(out, "输出缓冲区不能为空");
        CheckUtil.mustTrue(len > 0, "派生长度必须大于 0");
        int hLen = mac.getMacSize();
        int n = (len + hLen - 1) / hLen;
        byte[] dk = new byte[n * hLen];
        byte[] block = new byte[4];
        byte[] u = new byte[hLen];
        byte[] t = new byte[hLen];
        int off = 0;
        for (int i = 1; i <= n; i++) {
            // U1 = PRF(P, S || INT32_BE(i))
            block[0] = (byte) (i >>> 24);
            block[1] = (byte) (i >>> 16);
            block[2] = (byte) (i >>> 8);
            block[3] = (byte) i;
            mac.init(new KeyParameter(password));
            mac.update(salt, 0, salt.length);
            mac.update(block, 0, 4);
            mac.doFinal(u, 0);
            System.arraycopy(u, 0, t, 0, hLen);
            // T = U1 XOR U2 XOR ... XOR Uc
            for (int j = 1; j < iterationCount; j++) {
                mac.init(new KeyParameter(password));
                mac.update(u, 0, hLen);
                mac.doFinal(u, 0);
                for (int k = 0; k < hLen; k++) {
                    t[k] ^= u[k];
                }
            }
            System.arraycopy(t, 0, dk, off, hLen);
            off += hLen;
        }
        System.arraycopy(dk, 0, out, outOff, len);
        java.util.Arrays.fill(dk, (byte) 0);
        return len;
    }

    @Override
    public Mac getMac() {
        return mac;
    }
}
