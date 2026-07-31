package com.flora.crypto.core;
import com.flora.crypto.core.engine.JdkDigest;
import com.flora.crypto.core.engine.JdkBlockCipher;
import com.flora.crypto.core.engine.JdkAsymmetricBlockCipher;
import com.flora.crypto.core.engine.JdkMac;
import com.flora.crypto.core.engine.JdkKeyPairGenerator;
import com.flora.crypto.core.engine.JdkAgreement;
import com.flora.crypto.core.engine.JdkAsymmetricKeyPairGenerator;
import com.flora.crypto.core.engine.AgreementBasedKem;
import com.flora.crypto.core.engine.SecureRandomEntropySource;
import com.flora.crypto.core.engine.HMacDrbg;

import com.flora.crypto.core.KEM;
import com.flora.crypto.core.PlaceholderKem;
import com.flora.crypto.core.EntropySource;
import com.flora.crypto.core.SP80090DRBG;

import com.flora.crypto.core.padding.PKCS7Padding;
import com.flora.crypto.core.padding.ISO7816d4Padding;
import com.flora.crypto.core.padding.ZeroBytePadding;

import com.flora.java.CheckUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 加密组件注册表（模仿 JCA 的 {@code Provider} / BouncyCastleProvider 模式）。
 * <p>按字符串算法名取得对应接口的 JDK 原语适配器，调用方只依赖本类与角色接口。
 * 查询只返回<b>原语</b>（裸分组密码、裸 RSA、哈希、MAC、协商、密钥对生成）；
 * 模式/填充/缓冲等组合由自研组合层（{@code mode}/{@code padding} 包）以对象方式编排，
 * 不通过 JDK 变换字符串组合。</p>
 *
 * <pre>{@code
 * Digest d = CryptoProvider.digest("SHA-256");
 * BlockCipher aes = CryptoProvider.blockCipher("AES");        // 裸块引擎
 * BlockCipher cbc = new CBCBlockCipher(aes);                   // 自研组合
 * }</pre>
 *
 * <h2>自定义算法注册</h2>
 * 可把实现了 {@code core} 角色接口的自定义算法注册进来，按名优先加载：
 *
 * <pre>{@code
 * CryptoProvider.registerDigest("MyHash", MyDigest::new);
 * Digest d = CryptoProvider.digest("MyHash");   // 返回 MyDigest，而非 JDK 适配器
 * }</pre>
 *
 * 查找顺序为：<b>自定义注册表优先，未命中再回退到 JDK 适配器</b>。注册发生在 JVM 全局，请尽早（如启动时）完成。
 */
public final class CryptoProvider {

    private CryptoProvider() {
    }

    // ── 自定义注册表：每个角色一张，按名字优先命中 ──
    private static final Map<String, Supplier<? extends Digest>> DIGEST_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends BlockCipher>> BLOCK_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends AsymmetricBlockCipher>> ASYM_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends Mac>> MAC_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends JdkKeyPairGenerator>> KPG_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends Xof>> XOF_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends AsymmetricCipher>> ASYM_CIPHER_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends Agreement>> AGREEMENT_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends DerivationFunction>> DERIVATION_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends BlockCipherPadding>> PADDING_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends AsymmetricCipherKeyPairGenerator>> ASYM_KPG_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends KEM>> KEM_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends EntropySource>> ENTROPY_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends SP80090DRBG>> DRBG_REGISTRY = new ConcurrentHashMap<>();

    static {
        // 随附两个纯 Java KDF 实现，按名即可直接使用（无需注册）
        DERIVATION_REGISTRY.put("KDF2", () -> new Kdf2DerivationFunction(digest("SHA-256")));
        DERIVATION_REGISTRY.put("HKDF", () -> new HkdfDerivationFunction(mac("HmacSHA256")));
        // 常用填充策略预注册
        PADDING_REGISTRY.put("PKCS7", PKCS7Padding::new);
        PADDING_REGISTRY.put("PKCS5", PKCS7Padding::new);
        PADDING_REGISTRY.put("ISO7816", ISO7816d4Padding::new);
        PADDING_REGISTRY.put("ISO7816-4", ISO7816d4Padding::new);
        PADDING_REGISTRY.put("ZeroByte", ZeroBytePadding::new);
    }

    // ── 注册入口 ──

    public static void registerDigest(String name, Supplier<? extends Digest> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        DIGEST_REGISTRY.put(name, factory);
    }

    public static void registerBlockCipher(String name, Supplier<? extends BlockCipher> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        BLOCK_REGISTRY.put(name, factory);
    }

    public static void registerAsymmetricCipher(String name, Supplier<? extends AsymmetricBlockCipher> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        ASYM_REGISTRY.put(name, factory);
    }

    public static void registerMac(String name, Supplier<? extends Mac> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        MAC_REGISTRY.put(name, factory);
    }

    public static void registerKeyPairGenerator(String name, Supplier<? extends JdkKeyPairGenerator> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        KPG_REGISTRY.put(name, factory);
    }

    public static void registerXof(String name, Supplier<? extends Xof> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        XOF_REGISTRY.put(name, factory);
    }

    public static void registerAsymmetricStreamCipher(String name, Supplier<? extends AsymmetricCipher> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        ASYM_CIPHER_REGISTRY.put(name, factory);
    }

    public static void registerAgreement(String name, Supplier<? extends Agreement> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        AGREEMENT_REGISTRY.put(name, factory);
    }

    public static void registerDerivationFunction(String name, Supplier<? extends DerivationFunction> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        DERIVATION_REGISTRY.put(name, factory);
    }

    public static void registerBlockCipherPadding(String name, Supplier<? extends BlockCipherPadding> factory) {
        CheckUtil.notEmpty(name, "填充名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        PADDING_REGISTRY.put(name, factory);
    }

    public static void registerAsymmetricKeyPairGenerator(String name, Supplier<? extends AsymmetricCipherKeyPairGenerator> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        ASYM_KPG_REGISTRY.put(name, factory);
    }

    public static void registerKem(String name, Supplier<? extends KEM> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        KEM_REGISTRY.put(name, factory);
    }

    public static void registerEntropySource(String name, Supplier<? extends EntropySource> factory) {
        CheckUtil.notEmpty(name, "熵源名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        ENTROPY_REGISTRY.put(name, factory);
    }

    public static void registerDrbg(String name, Supplier<? extends SP80090DRBG> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        DRBG_REGISTRY.put(name, factory);
    }

    // ── 查询入口：注册表优先，未命中回退 JDK 原语适配器 ──

    public static Digest digest(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends Digest> f = DIGEST_REGISTRY.get(name);
        return f != null ? f.get() : JdkDigest.of(name);
    }

    /**
     * 获取裸分组密码原语（如 {@code "AES"}）。
     * <p>只接受裸算法名；组合变换（CBC/GCM/PKCS7 等）由自研组合层编排，
     * 不接受 {@code "AES/CBC/PKCS5Padding"} 形式的 JDK 变换字符串。</p>
     */
    public static BlockCipher blockCipher(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        rejectTransformation(algorithm, "blockCipher");
        Supplier<? extends BlockCipher> f = BLOCK_REGISTRY.get(algorithm);
        return f != null ? f.get() : JdkBlockCipher.of(algorithm);
    }

    /** 获取裸非对称原语（如 {@code "RSA"}），不接受组合变换字符串。 */
    public static AsymmetricBlockCipher asymmetricCipher(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        rejectTransformation(name, "asymmetricCipher");
        Supplier<? extends AsymmetricBlockCipher> f = ASYM_REGISTRY.get(name);
        return f != null ? f.get() : JdkAsymmetricBlockCipher.of(name);
    }

    public static Mac mac(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends Mac> f = MAC_REGISTRY.get(name);
        return f != null ? f.get() : JdkMac.of(name);
    }

    public static JdkKeyPairGenerator keyPairGenerator(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends JdkKeyPairGenerator> f = KPG_REGISTRY.get(name);
        return f != null ? f.get() : JdkKeyPairGenerator.of(name);
    }

    // ── 扩展角色查询入口（对齐 Bouncy Castle 轻量 API）──

    /**
     * 扩展摘要（含内部块长度）。
     *
     * @throws ClassCastException 若同名注册的自定义实现未实现 {@link ExtendedDigest}
     */
    public static ExtendedDigest extendedDigest(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        return (ExtendedDigest) digest(name);
    }

    /**
     * 可变长输出函数（XOF）。JDK 无对应能力，默认返回最简占位实现 {@link PlaceholderXof}，
     * 注册真实引擎（如 SHAKE）后按名优先返回。
     */
    public static Xof xof(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends Xof> f = XOF_REGISTRY.get(name);
        return f != null ? f.get() : new PlaceholderXof();
    }

    /**
     * 流式非对称密码。默认以 {@link BufferedAsymmetricBlockCipher} 包裹同名非对称分组密码。
     */
    public static AsymmetricCipher asymmetricStreamCipher(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends AsymmetricCipher> f = ASYM_CIPHER_REGISTRY.get(name);
        return f != null ? f.get() : new BufferedAsymmetricBlockCipher(asymmetricCipher(name));
    }

    /** 密钥协商（如 ECDH / DH / X25519）。 */
    public static Agreement agreement(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends Agreement> f = AGREEMENT_REGISTRY.get(name);
        return f != null ? f.get() : JdkAgreement.of(name);
    }

    /**
     * 密钥派生函数（KDF）。JCA 无第一等 KDF 抽象，默认返回占位实现；
     * 已预注册 {@code "KDF2"} / {@code "HKDF"} 两个纯 Java 实现。
     */
    public static DerivationFunction derivationFunction(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends DerivationFunction> f = DERIVATION_REGISTRY.get(name);
        return f != null ? f.get() : new PlaceholderDerivationFunction();
    }

    /** 分组密码填充策略。 */
    public static BlockCipherPadding blockCipherPadding(String name) {
        CheckUtil.notEmpty(name, "填充名不能为空");
        Supplier<? extends BlockCipherPadding> f = PADDING_REGISTRY.get(name);
        if (f != null) {
            return f.get();
        }
        throw new IllegalArgumentException("不支持的填充策略: " + name);
    }

    /** 轻量级非对称密钥对生成器（返回 {@link AsymmetricCipherKeyPair}）。 */
    public static AsymmetricCipherKeyPairGenerator asymmetricKeyPairGenerator(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends AsymmetricCipherKeyPairGenerator> f = ASYM_KPG_REGISTRY.get(name);
        return f != null ? f.get() : JdkAsymmetricKeyPairGenerator.of(name);
    }

    /**
     * 密钥封装机制（KEM）。默认实现 {@link AgreementBasedKem} 支持经典协商算法
     * （{@code ECDH} / {@code X25519} / {@code X448} / {@code DH}）；其余（如后量子 ML-KEM）
     * 无 JDK 引擎，返回占位 {@link PlaceholderKem}，注册真实引擎后按名优先返回。
     */
    public static KEM kem(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends KEM> f = KEM_REGISTRY.get(name);
        if (f != null) {
            return f.get();
        }
        return switch (name) {
            case "ECDH", "X25519", "X448", "DH" -> AgreementBasedKem.of(name);
            default -> new PlaceholderKem();
        };
    }

    /**
     * 熵源（供 DRBG 取种）。默认返回基于 JDK {@link java.security.SecureRandom} 的实现。
     */
    public static EntropySource entropySource(String name) {
        CheckUtil.notEmpty(name, "熵源名不能为空");
        Supplier<? extends EntropySource> f = ENTROPY_REGISTRY.get(name);
        return f != null ? f.get() : new SecureRandomEntropySource();
    }

    /** 默认熵源（名为 {@code "default"}）。 */
    public static EntropySource entropySource() {
        return entropySource("default");
    }

    /**
     * NIST SP800-90A HMAC_DRBG。默认以指定 HMAC 算法 + JDK 熵源构建；
     * 注册同名 DRBG 后优先返回（如替换为 CTR_DRBG / Hash_DRBG）。
     *
     * @param hmacAlgorithm       底层 HMAC（如 {@code "HmacSHA256"}）
     * @param securityStrengthBits 安全强度（位），应 ≤ HMAC 输出长度一半
     * @param personalizationString 个性化字符串（可为 {@code null}）
     */
    public static SP80090DRBG hmacDrbg(String hmacAlgorithm, int securityStrengthBits, byte[] personalizationString) {
        CheckUtil.notEmpty(hmacAlgorithm, "HMAC 算法名不能为空");
        Supplier<? extends SP80090DRBG> f = DRBG_REGISTRY.get(hmacAlgorithm);
        if (f != null) {
            return f.get();
        }
        return new HMacDrbg(mac(hmacAlgorithm), new SecureRandomEntropySource(), securityStrengthBits, personalizationString);
    }

    private static void rejectTransformation(String name, String entry) {
        if (name.indexOf('/') >= 0) {
            throw new IllegalArgumentException(entry + " 只接受裸算法名，组合变换由自研组合层编排: " + name);
        }
    }
}
