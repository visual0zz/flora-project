package com.flora.crypto.core.engine;

import com.flora.crypto.core.CipherParameters;
import com.flora.crypto.core.KeyParameter;
import com.flora.crypto.core.PBEParametersGenerator;

import com.flora.java.CheckUtil;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

/**
 * 把 JDK 的 PBKDF2（经 {@link SecretKeyFactory}）接入 {@link PBEParametersGenerator}。
 * <p>JDK 原生仅支持 PBKDF2（如 {@code "PBKDF2WithHmacSHA256"}）；scrypt / bcrypt / Argon2
 * 等需自行实现后接入。</p>
 */
public final class JdkPBEParametersGenerator extends PBEParametersGenerator {

    private final String algorithm;

    private JdkPBEParametersGenerator(String algorithm) {
        this.algorithm = algorithm;
    }

    public static JdkPBEParametersGenerator of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkPBEParametersGenerator(algorithm);
    }

    private byte[] derive(int keySizeBits) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(algorithm);
            // 把字节口令按 0..255 映射到 char[]，避免 UTF-8 破坏任意二进制口令
            char[] pw = new char[password.length];
            for (int i = 0; i < password.length; i++) {
                pw[i] = (char) (password[i] & 0xFF);
            }
            PBEKeySpec spec = new PBEKeySpec(pw, salt, iterationCount, keySizeBits);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的 PBE 算法: " + algorithm, e);
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("PBE 派生失败: " + algorithm, e);
        }
    }

    @Override
    public CipherParameters generateDerivedParameters(int keySizeBits) {
        CheckUtil.mustTrue(keySizeBits > 0, "密钥位数必须大于 0");
        return new KeyParameter(derive(keySizeBits));
    }

    @Override
    public CipherParameters generateDerivedMacParameters(int keySizeBits) {
        CheckUtil.mustTrue(keySizeBits > 0, "MAC 密钥位数必须大于 0");
        return new KeyParameter(derive(keySizeBits));
    }
}
