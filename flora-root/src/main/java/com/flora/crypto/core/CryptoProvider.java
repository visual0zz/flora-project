package com.flora.crypto.core;

import com.flora.java.CheckUtil;

/**
 * 加密组件注册表（模仿 JCA 的 {@code Provider} / BouncyCastleProvider 模式）。
 * <p>按字符串算法名取得对应接口的 JDK 适配器实现，调用方只依赖本类与角色接口，
 * 不依赖任何具体适配器类，消除了「调用方直接 new 具体算法类」的耦合。</p>
 *
 * <pre>{@code
 * Digest d = CryptoProvider.digest("SHA-256");
 * BlockCipher aes = CryptoProvider.blockCipher("AES/CBC/PKCS5Padding");
 * Signer s = CryptoProvider.signer("SHA256withRSA");
 * }</pre>
 */
public final class CryptoProvider {

    private CryptoProvider() {
    }

    public static Digest digest(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        return JdkDigest.of(name);
    }

    public static BlockCipher blockCipher(String transformation) {
        CheckUtil.notEmpty(transformation, "变换字符串不能为空");
        return JdkBlockCipher.of(transformation);
    }

    public static StreamCipher streamCipher(String transformation) {
        CheckUtil.notEmpty(transformation, "变换字符串不能为空");
        return JdkStreamCipher.of(transformation);
    }

    public static AsymmetricBlockCipher asymmetricCipher(String name) {
        CheckUtil.notEmpty(name, "变换字符串不能为空");
        return JdkAsymmetricBlockCipher.of(name);
    }

    public static Mac mac(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        return JdkMac.of(name);
    }

    public static Signer signer(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        return JdkSigner.of(name);
    }

    public static JdkKeyPairGenerator keyPairGenerator(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        return JdkKeyPairGenerator.of(name);
    }
}
