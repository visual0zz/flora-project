package com.flora.crypto.core;
import com.flora.crypto.core.interfaces.provider.AlgorithmFamily;

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

import com.flora.crypto.core.interfaces.provider.Agreement;
import com.flora.crypto.core.interfaces.provider.AsymmetricBlockCipher;
import com.flora.crypto.core.interfaces.provider.AsymmetricCipher;
import com.flora.crypto.core.interfaces.provider.AsymmetricCipherKeyPairGenerator;
import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.interfaces.provider.BlockCipherPadding;
import com.flora.crypto.core.interfaces.provider.DerivationFunction;
import com.flora.crypto.core.interfaces.provider.Digest;
import com.flora.crypto.core.interfaces.provider.EntropySource;
import com.flora.crypto.core.interfaces.provider.ExtendedDigest;
import com.flora.crypto.core.interfaces.provider.KEM;
import com.flora.crypto.core.interfaces.provider.Mac;
import com.flora.crypto.core.interfaces.provider.SP80090DRBG;
import com.flora.crypto.core.interfaces.provider.Xof;

import com.flora.crypto.core.padding.PKCS7Padding;
import com.flora.crypto.core.padding.ISO7816d4Padding;
import com.flora.crypto.core.padding.ZeroBytePadding;

import com.flora.java.CheckUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 加密组件注册表（模仿 JCA 的 {@code Provider} / BouncyCastleProvider 模式）。
 * <p>实现类通过族接口（{@code Digest} / {@code Mac} / {@code BlockCipher} 等，均继承
 * {@code AlgorithmFamily}）自述支持的算法集合与优先级。注册表按算法名索引，
 * 同一算法名可被多个实现类注册，分发时按「能实现 → 优先级（越大越优先）→ 具体度（算法数越少越优先）」
 * 裁决，仍平局则抛异常。</p>
 *
 * <pre>{@code
 * Digest d = CryptoProvider.digest("SHA-256");
 * BlockCipher aes = CryptoProvider.blockCipher("AES");        // 裸块引擎
 * BlockCipher cbc = new CBCBlockCipher(aes);                   // 自研组合
 * }</pre>
 *
 * <h2>自定义算法注册</h2>
 * 以「原型实例 + 工厂」注册：原型实例通过族接口自述支持的算法与优先级。
 *
 * <pre>{@code
 * CryptoProvider.registerDigest(new MyDigest(), MyDigest::of);
 * Digest d = CryptoProvider.digest("MyHash");
 * }</pre>
 *
 * 注册发生在 JVM 全局，请尽早（如启动时）完成。
 */
public final class CryptoProvider {

    private CryptoProvider() {
    }

    // ── 注册表：算法名 → 提供者条目列表（每个条目含优先级/具体度/工厂）──
    private record Entry<T>(int priority, int specificity, Supplier<? extends T> factory) {
    }

    private static final Map<String, List<Entry<Digest>>> DIGEST_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<BlockCipher>>> BLOCK_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<AsymmetricBlockCipher>>> ASYM_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<Mac>>> MAC_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<JdkKeyPairGenerator>>> KPG_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<Xof>>> XOF_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<AsymmetricCipher>>> ASYM_CIPHER_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<Agreement>>> AGREEMENT_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<DerivationFunction>>> DERIVATION_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<BlockCipherPadding>>> PADDING_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<AsymmetricCipherKeyPairGenerator>>> ASYM_KPG_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<KEM>>> KEM_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<EntropySource>>> ENTROPY_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<Entry<SP80090DRBG>>> DRBG_REGISTRY = new ConcurrentHashMap<>();

    static {
        // JDK 原语适配器（通用，priority 默认 0）
        registerDigest(JdkDigest.of("SHA-256"), JdkDigest::of);
        registerBlockCipher(JdkBlockCipher.of("AES"), JdkBlockCipher::of);
        registerMac(JdkMac.of("HmacSHA256"), JdkMac::of);
        registerAsymmetricCipher(JdkAsymmetricBlockCipher.of("RSA"), JdkAsymmetricBlockCipher::of);
        registerAgreement(JdkAgreement.of("ECDH"), JdkAgreement::of);
        registerKeyPairGenerator(JdkKeyPairGenerator.of("RSA"), JdkKeyPairGenerator::of);
        registerAsymmetricKeyPairGenerator(JdkAsymmetricKeyPairGenerator.of("EC"), JdkAsymmetricKeyPairGenerator::of);
        registerKem(AgreementBasedKem.of("ECDH"), AgreementBasedKem::of);
        // 自研组合类（单算法）
        registerDerivationFunction(new Kdf2DerivationFunction(digest("SHA-256")),
                n -> new Kdf2DerivationFunction(digest("SHA-256")));
        registerDerivationFunction(new HkdfDerivationFunction(mac("HmacSHA256")),
                n -> new HkdfDerivationFunction(mac("HmacSHA256")));
        registerBlockCipherPadding(new PKCS7Padding(), n -> switch (n) {
            case "PKCS7", "PKCS5" -> new PKCS7Padding();
            default -> throw new IllegalArgumentException(n);
        });
        registerBlockCipherPadding(new ISO7816d4Padding(), n -> switch (n) {
            case "ISO7816", "ISO7816-4" -> new ISO7816d4Padding();
            default -> throw new IllegalArgumentException(n);
        });
        registerBlockCipherPadding(new ZeroBytePadding(), n -> new ZeroBytePadding());
    }

    // ── 注册入口：原型实例（经族接口自述）+ 按名工厂 ──

    public static void registerDigest(Digest prototype, Function<String, ? extends Digest> factory) {
        register(DIGEST_REGISTRY, prototype, factory);
    }

    public static void registerBlockCipher(BlockCipher prototype, Function<String, ? extends BlockCipher> factory) {
        register(BLOCK_REGISTRY, prototype, factory);
    }

    public static void registerAsymmetricCipher(AsymmetricBlockCipher prototype, Function<String, ? extends AsymmetricBlockCipher> factory) {
        register(ASYM_REGISTRY, prototype, factory);
    }

    public static void registerMac(Mac prototype, Function<String, ? extends Mac> factory) {
        register(MAC_REGISTRY, prototype, factory);
    }

    public static void registerKeyPairGenerator(JdkKeyPairGenerator prototype, Function<String, ? extends JdkKeyPairGenerator> factory) {
        register(KPG_REGISTRY, prototype, factory);
    }

    public static void registerXof(Xof prototype, Function<String, ? extends Xof> factory) {
        register(XOF_REGISTRY, prototype, factory);
    }

    public static void registerAsymmetricStreamCipher(AsymmetricCipher prototype, Function<String, ? extends AsymmetricCipher> factory) {
        register(ASYM_CIPHER_REGISTRY, prototype, factory);
    }

    public static void registerAgreement(Agreement prototype, Function<String, ? extends Agreement> factory) {
        register(AGREEMENT_REGISTRY, prototype, factory);
    }

    public static void registerDerivationFunction(DerivationFunction prototype, Function<String, ? extends DerivationFunction> factory) {
        register(DERIVATION_REGISTRY, prototype, factory);
    }

    public static void registerBlockCipherPadding(BlockCipherPadding prototype, Function<String, ? extends BlockCipherPadding> factory) {
        register(PADDING_REGISTRY, prototype, factory);
    }

    public static void registerAsymmetricKeyPairGenerator(AsymmetricCipherKeyPairGenerator prototype, Function<String, ? extends AsymmetricCipherKeyPairGenerator> factory) {
        register(ASYM_KPG_REGISTRY, prototype, factory);
    }

    public static void registerKem(KEM prototype, Function<String, ? extends KEM> factory) {
        register(KEM_REGISTRY, prototype, factory);
    }

    public static void registerEntropySource(EntropySource prototype, Function<String, ? extends EntropySource> factory) {
        register(ENTROPY_REGISTRY, prototype, factory);
    }

    public static void registerDrbg(SP80090DRBG prototype, Function<String, ? extends SP80090DRBG> factory) {
        register(DRBG_REGISTRY, prototype, factory);
    }

    private static <T> void register(Map<String, List<Entry<T>>> reg,
                                     T prototype,
                                     Function<String, ? extends T> factory) {
        if (!(prototype instanceof AlgorithmFamily family)) {
            throw new IllegalArgumentException("原型实例必须实现 AlgorithmFamily: " + prototype.getClass());
        }
        Set<String> algorithms = family.supportedAlgorithms();
        if (algorithms == null || algorithms.isEmpty()) {
            throw new IllegalArgumentException("supportedAlgorithms() 不能为空: " + prototype.getClass());
        }
        int priority = family.priority();
        int specificity = algorithms.size();
        for (String name : algorithms) {
            reg.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>())
                    .add(new Entry<>(priority, specificity, () -> factory.apply(name)));
        }
    }

    // ── 分发入口：按「能实现 → 优先级 → 具体度」裁决 ──

    public static Digest digest(String name) {
        return resolve(DIGEST_REGISTRY, name, "摘要算法");
    }

    public static BlockCipher blockCipher(String name) {
        return resolve(BLOCK_REGISTRY, name, "分组密码");
    }

    public static AsymmetricBlockCipher asymmetricCipher(String name) {
        return resolve(ASYM_REGISTRY, name, "非对称密码");
    }

    public static Mac mac(String name) {
        return resolve(MAC_REGISTRY, name, "MAC 算法");
    }

    public static JdkKeyPairGenerator keyPairGenerator(String name) {
        return resolve(KPG_REGISTRY, name, "密钥对生成器");
    }

    /** @throws ClassCastException 若裁决出的实现未实现 {@link ExtendedDigest} */
    public static ExtendedDigest extendedDigest(String name) {
        return (ExtendedDigest) digest(name);
    }

    /** 可变长输出函数；未注册返回占位实现。 */
    public static Xof xof(String name) {
        return resolveOrElse(XOF_REGISTRY, name, new PlaceholderXof());
    }

    /** 流式非对称密码；默认以 {@link BufferedAsymmetricBlockCipher} 包裹同名非对称分组密码。 */
    public static AsymmetricCipher asymmetricStreamCipher(String name) {
        return resolveOrElse(ASYM_CIPHER_REGISTRY, name,
                new BufferedAsymmetricBlockCipher(asymmetricCipher(name)));
    }

    public static Agreement agreement(String name) {
        return resolve(AGREEMENT_REGISTRY, name, "密钥协商算法");
    }

    public static DerivationFunction derivationFunction(String name) {
        return resolveOrElse(DERIVATION_REGISTRY, name, new PlaceholderDerivationFunction());
    }

    public static BlockCipherPadding blockCipherPadding(String name) {
        return resolve(PADDING_REGISTRY, name, "填充策略");
    }

    public static AsymmetricCipherKeyPairGenerator asymmetricKeyPairGenerator(String name) {
        return resolve(ASYM_KPG_REGISTRY, name, "非对称密钥对生成器");
    }

    public static KEM kem(String name) {
        return resolveOrElse(KEM_REGISTRY, name, new PlaceholderKem());
    }

    public static EntropySource entropySource(String name) {
        return resolveOrElse(ENTROPY_REGISTRY, name, new SecureRandomEntropySource());
    }

    public static EntropySource entropySource() {
        return entropySource("default");
    }

    public static SP80090DRBG hmacDrbg(String hmacAlgorithm, int securityStrengthBits, byte[] personalizationString) {
        CheckUtil.notEmpty(hmacAlgorithm, "HMAC 算法名不能为空");
        return resolveOrElse(DRBG_REGISTRY, hmacAlgorithm,
                new HMacDrbg(mac(hmacAlgorithm), new SecureRandomEntropySource(), securityStrengthBits, personalizationString));
    }

    private static <T> T resolve(Map<String, List<Entry<T>>> reg, String name, String role) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        List<Entry<T>> list = reg.get(name);
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("未注册的" + role + ": " + name);
        }
        T result = pick(list, name);
        if (result == null) {
            throw new IllegalArgumentException("算法重复注册: " + name + " 存在多个同优先级同具体度的提供者");
        }
        return result;
    }

    private static <T> T resolveOrElse(Map<String, List<Entry<T>>> reg, String name, T fallback) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        List<Entry<T>> list = reg.get(name);
        if (list == null || list.isEmpty()) {
            return fallback;
        }
        T result = pick(list, name);
        return result != null ? result : fallback;
    }

    /** 按「优先级最大 → 具体度最小」裁决，多个并列返回 null。 */
    private static <T> T pick(List<Entry<T>> list, String name) {
        int maxPri = list.stream().mapToInt(Entry::priority).max().orElse(0);
        var byPri = list.stream().filter(e -> e.priority() == maxPri).toList();
        if (byPri.size() == 1) {
            return byPri.get(0).factory().get();
        }
        int minSpec = byPri.stream().mapToInt(Entry::specificity).min().orElse(0);
        var bySpec = byPri.stream().filter(e -> e.specificity() == minSpec).toList();
        if (bySpec.size() == 1) {
            return bySpec.get(0).factory().get();
        }
        return null;
    }
}
