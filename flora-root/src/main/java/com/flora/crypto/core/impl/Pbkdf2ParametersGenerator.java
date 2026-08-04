package com.flora.crypto.core.impl;

import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.crypto.core.param.KeyParameter;
import com.flora.crypto.core.interfaces.provider.Mac;
import com.flora.crypto.core.PBEParametersGenerator;
import com.flora.java.CheckUtil;

/**
 * PBKDF2 口令派生（RFC 8018 §5.2，自研组合层）。
 * <p>以 {@link Mac}（如 HmacSHA256）为原语迭代：{@code DK = T1‖T2‖...‖Tn}，
 * {@code Ti = U1⊕U2⊕...⊕Uc}，{@code U1 = PRF(P, S ‖ INT32_BE(i))}，
 * {@code Uj = PRF(P, U(j-1))}。替代 JDK {@code SecretKeyFactory} 的 PBKDF2 组合结构。</p>
 */
public final class Pbkdf2ParametersGenerator extends PBEParametersGenerator {

    private final Mac prf;

    public Pbkdf2ParametersGenerator(Mac prf) {
        CheckUtil.notNull(prf, "PRF 不能为空");
        this.prf = prf;
    }

    @Override
    public CipherParameters generateDerivedParameters(int keySizeBits) {
        int keySize = keySizeBits / 8;
        if (keySizeBits % 8 != 0) {
            throw new IllegalArgumentException("密钥位数必须为 8 的倍数");
        }
        return new KeyParameter(generateDerivedKey(keySize));
    }

    @Override
    public CipherParameters generateDerivedMacParameters(int keySizeBits) {
        return generateDerivedParameters(keySizeBits);
    }

    private byte[] generateDerivedKey(int dkLen) {
        int hLen = prf.getMacSize();
        int n = (dkLen + hLen - 1) / hLen;
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
            prf.init(new KeyParameter(password));
            prf.update(salt, 0, salt.length);
            prf.update(block, 0, 4);
            prf.doFinal(u, 0);
            System.arraycopy(u, 0, t, 0, hLen);
            // T = U1 XOR U2 XOR ... XOR Uc
            for (int j = 1; j < iterationCount; j++) {
                prf.init(new KeyParameter(password));
                prf.update(u, 0, hLen);
                prf.doFinal(u, 0);
                for (int k = 0; k < hLen; k++) {
                    t[k] ^= u[k];
                }
            }
            System.arraycopy(t, 0, dk, off, hLen);
            off += hLen;
        }
        // 截取前 dkLen 字节
        byte[] result = new byte[dkLen];
        System.arraycopy(dk, 0, result, 0, dkLen);
        java.util.Arrays.fill(dk, (byte) 0);
        return result;
    }
}
