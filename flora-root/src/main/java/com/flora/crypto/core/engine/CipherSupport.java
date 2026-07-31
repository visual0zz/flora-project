package com.flora.crypto.core.engine;
import com.flora.crypto.core.CipherParameters;
import com.flora.crypto.core.KeyParameter;
import com.flora.crypto.core.AsymmetricKeyParameter;
import com.flora.crypto.core.ParametersWithIV;

import javax.crypto.spec.SecretKeySpec;

/**
 * JDK 原语适配器共用的内部支撑方法（包级私有，不导出）。
 */
final class CipherSupport {

    private CipherSupport() {
    }

    static KeyParameter keyParameter(CipherParameters params) {
        CipherParameters p = params;
        if (p instanceof ParametersWithIV) {
            p = ((ParametersWithIV) p).getParameters();
        }
        if (p instanceof KeyParameter) {
            return (KeyParameter) p;
        }
        throw new IllegalArgumentException("需要 KeyParameter（对称密钥）");
    }

    static AsymmetricKeyParameter asymmetricKeyParameter(CipherParameters params) {
        if (params instanceof AsymmetricKeyParameter) {
            return (AsymmetricKeyParameter) params;
        }
        throw new IllegalArgumentException("需要 AsymmetricKeyParameter（非对称密钥）");
    }

    static byte[] ivOf(CipherParameters params) {
        return params instanceof ParametersWithIV ? ((ParametersWithIV) params).getIV() : null;
    }

    static SecretKeySpec secretKey(CipherParameters params, String algorithm) {
        return new SecretKeySpec(keyParameter(params).getKey(), algorithm);
    }
}
