package com.flora.crypto.core;

import com.flora.crypto.core.bridge.JdkKeyPairGenerator;
import com.flora.crypto.core.factory.AgreementBasedKemFactory;
import com.flora.crypto.core.factory.Argon2Factory;
import com.flora.crypto.core.factory.Blake2b256Factory;
import com.flora.crypto.core.factory.Blake2b512Factory;
import com.flora.crypto.core.factory.Blake2bFactory;
import com.flora.crypto.core.factory.BCryptFactory;
import com.flora.crypto.core.factory.CbcFactory;
import com.flora.crypto.core.factory.CfbFactory;
import com.flora.crypto.core.factory.CtrFactory;
import com.flora.crypto.core.factory.GcmFactory;
import com.flora.crypto.core.factory.HMacFactory;
import com.flora.crypto.core.factory.HkdfFactory;
import com.flora.crypto.core.factory.Iso7816Factory;
import com.flora.crypto.core.factory.JdkAgreementFactory;
import com.flora.crypto.core.factory.JdkAsymmetricBlockCipherFactory;
import com.flora.crypto.core.factory.JdkAsymmetricKeyPairGeneratorFactory;
import com.flora.crypto.core.factory.JdkBlockCipherFactory;
import com.flora.crypto.core.factory.JdkDigestFactory;
import com.flora.crypto.core.factory.JdkKemFactory;
import com.flora.crypto.core.factory.JdkKeyPairGeneratorFactory;
import com.flora.crypto.core.factory.JdkMacFactory;
import com.flora.crypto.core.factory.Kdf2Factory;
import com.flora.crypto.core.factory.OfbFactory;
import com.flora.crypto.core.factory.Pbkdf2Factory;
import com.flora.crypto.core.factory.Pkcs7Factory;
import com.flora.crypto.core.factory.Poly1305Factory;
import com.flora.crypto.core.factory.Ripemd160Factory;
import com.flora.crypto.core.factory.ScryptFactory;
import com.flora.crypto.core.factory.ZeroByteFactory;
import com.flora.crypto.core.combinator.BufferedAsymmetricBlockCipher;
import com.flora.crypto.core.impl.HMacDrbg;
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
import com.flora.crypto.core.interfaces.provider.KeyEncapsulationMechanism;
import com.flora.crypto.core.interfaces.provider.Mac;
import com.flora.crypto.core.interfaces.provider.DeterministicRandomBitGenerator;
import com.flora.crypto.core.interfaces.provider.ExtendableOutputFunction;
import com.flora.crypto.core.bridge.SecureRandomEntropySource;
import com.flora.java.CheckUtil;
import com.flora.tag.ModuleEntry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 加密组件注册表（DSL 表达式驱动）。
 * <p>算法组件通过 DSL 表达式注册和解析：</p>
 * <pre>
 * 表达式 = 裸名 | 裸名(表达式, ...) | 字面量
 * 字面量 = integer:数字 | float:小数 | string:文本 | bytes:十六进制
 * </pre>
 * <p>注册时按算法族（{@link AlgorithmKind}）分类，并指定角色类型（如 {@link Digest}、{@link Mac}）。
 * 同名算法可安全注册在不同族下。JDK 适配器与其它算法一样作为普通条目注册于对应族，不享受特殊回退通道；
 * 类型化查询（如 {@link #digest(String)}）优先在本族查找，未命中则跨所有族统一解析（DSL 子依赖亦跨族搜索）。</p>
 * <p><b>组合算法须以 DSL 带参形式调用</b>，例如 {@code "PBKDF2(HMac(SHA-256))"}、{@code "HMac(SHA-256)"}、
 * {@code "CBC(AES)"}。以裸名（无参）查询一个需要参数的组合算法（如 {@code "HMac"}）会抛出
 * {@link IllegalArgumentException}，而不是用空参数数组调用工厂。</p>
 * <pre>{@code
 * CryptoProvider.register(AlgorithmKind.DERIVATION, new Pbkdf2Factory());
 * // 查询时 DSL 自动解析依赖：
 * DerivationFunction kdf = CryptoProvider.derivationFunction("PBKDF2(HMac(SHA-256))");
 * }</pre>
 * <p>表达式确实无法解析到任何已注册工厂时抛出 {@link UnregisteredAlgorithmException}；
 * 需要「未注册即兜底」语义的查询方法（如 {@link #derivationFunction(String)}）仅捕获该异常以返回占位实现，
 * 不会掩盖参数缺失 / 类型错误等真正的配置问题。</p>
 */
@ModuleEntry
public final class CryptoProvider {

    private CryptoProvider() {}

    // ── 按算法族分类注册表 ──

    /**
     * 注册表条目。
     * <ul>
     *   <li>{@code takesArguments}：工厂是否依赖调用方解析出的参数（组合算法为 true，
     *       JDK 适配器与无参原语为 false）。裸名查询命中 {@code takesArguments=true} 的工厂时视为参数缺失。</li>
     *   <li>{@code paramTypes}：工厂各参数的期望类型，用于在调用前做运行时类型校验（见 {@code applyEntry}）。</li>
     * </ul>
     */
    private record Entry(int priority, int specificity, boolean takesArguments,
                         Class<?>[] paramTypes, Function<Object[], Object> factory) {}

    /** 算法族 → (DSL 名称 → 候选条目列表)。同名算法在不同族下互不干扰。 */
    private static final Map<AlgorithmKind, Map<String, List<Entry>>> ROLES = new ConcurrentHashMap<>();

    static {
        // ── JDK 适配器（每条语句按类批量注册其支持的全部算法名，priority=0）──
        register(AlgorithmKind.DIGEST, JdkDigestFactory.class);
        register(AlgorithmKind.BLOCK_CIPHER, JdkBlockCipherFactory.class);
        register(AlgorithmKind.MAC, JdkMacFactory.class);
        register(AlgorithmKind.ASYMMETRIC_BLOCK_CIPHER, JdkAsymmetricBlockCipherFactory.class);
        register(AlgorithmKind.AGREEMENT, JdkAgreementFactory.class);
        register(AlgorithmKind.KEY_PAIR_GENERATOR, JdkKeyPairGeneratorFactory.class);
        register(AlgorithmKind.ASYMMETRIC_KEY_PAIR_GENERATOR, JdkAsymmetricKeyPairGeneratorFactory.class);
        register(AlgorithmKind.KEM, AgreementBasedKemFactory.class);
        register(AlgorithmKind.KEM, JdkKemFactory.class);

        // ── KDF（依赖其他算法，DSL 自动解析）──
        register(AlgorithmKind.DERIVATION, new Kdf2Factory());
        register(AlgorithmKind.DERIVATION, new Pbkdf2Factory());
        register(AlgorithmKind.DERIVATION, new HkdfFactory());

        // ── 纯 Java 摘要实现 ──
        register(AlgorithmKind.DIGEST, new Blake2bFactory());
        register(AlgorithmKind.DIGEST, new Blake2b256Factory());
        register(AlgorithmKind.DIGEST, new Blake2b512Factory());
        register(AlgorithmKind.DIGEST, new Ripemd160Factory());

        // ── 纯 Java MAC ──
        register(AlgorithmKind.MAC, new Poly1305Factory());
        register(AlgorithmKind.MAC, new HMacFactory());

        // ── 密码哈希 / KDF（盐、迭代、内存等参数经 init 传入）──
        register(AlgorithmKind.DERIVATION, new Argon2Factory());
        register(AlgorithmKind.DERIVATION, new BCryptFactory());
        register(AlgorithmKind.DERIVATION, new ScryptFactory());

        // ── 分组密码模式 ──
        register(AlgorithmKind.BLOCK_CIPHER, new CbcFactory());
        register(AlgorithmKind.BLOCK_CIPHER, new CfbFactory());
        register(AlgorithmKind.BLOCK_CIPHER, new OfbFactory());
        register(AlgorithmKind.BLOCK_CIPHER, new CtrFactory());
        register(AlgorithmKind.BLOCK_CIPHER, new GcmFactory());

        // ── 填充策略 ──
        register(AlgorithmKind.BLOCK_CIPHER_PADDING, new Pkcs7Factory());
        register(AlgorithmKind.BLOCK_CIPHER_PADDING, new Iso7816Factory());
        register(AlgorithmKind.BLOCK_CIPHER_PADDING, new ZeroByteFactory());
    }

    // ── 注册 API ──

    /**
     * 注册一个算法工厂。
     * <p>工厂自述 DSL 名、优先级、具体度与参数类型（见 {@link AlgorithmFactory}）；注册表会为工厂自述的
     * 每个名字都登记同一条目。同名同优先级的多个候选按「具体度最小」裁决，平局报错。</p>
     *
     * @param role    算法族（如 {@link AlgorithmKind#DIGEST}）
     * @param factory 自述型工厂
     */
    public static void register(AlgorithmKind role, AlgorithmFactory factory) {
        CheckUtil.notNull(role, "算法族不能为空");
        Set<String> names = factory.supportedAlgorithms();
        CheckUtil.mustTrue(names != null && !names.isEmpty(), "算法名集合不能为空");
        for (String name : names) {
            register(role, factory, name);
        }
    }

    /**
     * 按类注册一个算法工厂（一条语句完成多重注册）。
     * <p>工厂类须提供可访问的无参构造与实例方法 {@link AlgorithmFactory#chooseAlgorithm(String)}，
     * 且 {@link AlgorithmFactory#supportedAlgorithms()} 返回该类支持的全部 DSL 名（全集，与注入无关）。
     * 注册表先以无参构造创建一个原型实例取其全集，再为每个名字创建独立实例并注入算法名，
     * 最终委托 {@link #register(AlgorithmKind, AlgorithmFactory, String)} 逐名登记。</p>
     *
     * @param role  算法族（如 {@link AlgorithmKind#DIGEST}）
     * @param clazz 工厂类（实现 {@link AlgorithmFactory}）
     */
    public static void register(AlgorithmKind role, Class<? extends AlgorithmFactory> clazz) {
        CheckUtil.notNull(role, "算法族不能为空");
        CheckUtil.notNull(clazz, "工厂类不能为空");
        AlgorithmFactory prototype;
        try {
            prototype = clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    clazz.getSimpleName() + " 缺少可访问的无参构造器", e);
        }
        Set<String> names = prototype.supportedAlgorithms();
        CheckUtil.mustTrue(names != null && !names.isEmpty(),
                clazz.getSimpleName() + " 的 supportedAlgorithms() 返回空集合");
        for (String name : names) {
            AlgorithmFactory factory;
            try {
                factory = clazz.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        clazz.getSimpleName() + " 缺少可访问的无参构造器", e);
            }
            factory.chooseAlgorithm(name);
            register(role, factory, name);
        }
    }

    /**
     * 将单个算法名登记到注册表。
     *
     * @param role    算法族
     * @param factory 生产该算法的工厂实例
     * @param name    算法 DSL 名
     */
    private static void register(AlgorithmKind role, AlgorithmFactory factory, String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        int priority = factory.priority();
        int specificity = factory.specificity();
        Class<?>[] paramTypes = factory.paramTypes();
        boolean takesArguments = paramTypes != null && paramTypes.length > 0;
        ROLES.computeIfAbsent(role, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(name, k -> new CopyOnWriteArrayList<>())
                .add(new Entry(priority, specificity, takesArguments, paramTypes, factory::create));
    }

    // ── DSL 解析与裁决 ──

    /**
     * 解析 DSL 表达式并返回构造好的实例（跨所有算法族搜索）。
     *
     * @param expression DSL 表达式，如 {@code "PBKDF2(HMac(SHA-256))"}
     * @return 构造好的算法实例
     * @throws IllegalArgumentException 表达式无效或无匹配的工厂
     */
    public static Object resolve(String expression) {
        CheckUtil.notEmpty(expression, "DSL expression cannot be empty");
        return resolveExpr(DslParser.parse(expression), null);
    }

    /**
     * 内部统一解析入口。
     *
     * @param parsed     DslParser 的解析结果（String / Invocation / 字面量）
     * @param hintFamily 提示算法族（可为 null），优先在该族下查找
     */
    private static Object resolveExpr(Object parsed, AlgorithmKind hintFamily) {
        if (parsed instanceof String bareName) {
            return resolveName(bareName, hintFamily, new Object[0]);
        }
        if (parsed instanceof DslParser.Invocation inv) {
            Object[] args = resolveArgs(inv.args());
            return resolveName(inv.name(), hintFamily, args);
        }
        // 字面量直接返回
        return parsed;
    }

    private static Object resolveName(String name, AlgorithmKind hintFamily, Object[] args) {
        // 1. 提示算法族优先
        if (hintFamily != null) {
            Entry entry = findEntry(hintFamily, name);
            if (entry != null) return applyEntry(entry, name, args);
        }
        // 2. 跨算法族搜索（JDK 适配器与组合算法均为普通条目，统一在此解析）
        Entry entry = findAcrossRoles(name);
        if (entry != null) return applyEntry(entry, name, args);
        // 3. 未注册
        throw new UnregisteredAlgorithmException(name);
    }

    /**
     * 调用工厂前的统一前置校验。
     * <ul>
     *   <li>需参工厂（{@code takesArguments=true}）不得以裸名（无参）调用，否则抛清晰错误，
     *       而非用空参数数组调用工厂导致 {@link ArrayIndexOutOfBoundsException}。</li>
     *   <li>按注册时的 {@code paramTypes} 校验各实参的运行时类型，类型不符时给出可读错误，
     *       而非在工厂强转处抛 {@link ClassCastException}。</li>
     * </ul>
     */
    private static Object applyEntry(Entry entry, String name, Object[] args) {
        if (entry.takesArguments()) {
            if (args.length == 0) {
                throw new IllegalArgumentException(
                        "Algorithm '" + name + "' requires parameters; call it as a DSL expression, "
                                + "e.g. '" + name + "(...)')");
            }
            Class<?>[] types = entry.paramTypes();
            for (int i = 0; i < types.length; i++) {
                Object arg = i < args.length ? args[i] : null;
                if (arg == null || !types[i].isInstance(arg)) {
                    String actual = arg == null ? "null" : arg.getClass().getSimpleName();
                    throw new IllegalArgumentException(
                            "Argument " + i + " of '" + name + "' must be "
                                    + types[i].getSimpleName() + " but was " + actual);
                }
            }
        }
        return entry.factory().apply(args);
    }

    private static Entry findEntry(AlgorithmKind family, String name) {
        Map<String, List<Entry>> familyMap = ROLES.get(family);
        if (familyMap == null) return null;
        List<Entry> entries = familyMap.get(name);
        if (entries == null || entries.isEmpty()) return null;
        return select(entries, name);
    }

    private static Entry findAcrossRoles(String name) {
        Entry found = null;
        for (Map<String, List<Entry>> familyMap : ROLES.values()) {
            List<Entry> entries = familyMap.get(name);
            if (entries != null && !entries.isEmpty()) {
                Entry candidate = select(entries, name);
                if (found != null) {
                    throw new IllegalArgumentException(
                            "Ambiguous algorithm '" + name + "' found in multiple roles");
                }
                found = candidate;
            }
        }
        return found;
    }

    private static Object[] resolveArgs(Object[] rawArgs) {
        Object[] resolved = new Object[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            resolved[i] = resolveExpr(rawArgs[i], null);
        }
        return resolved;
    }

    private static Entry select(List<Entry> entries, String name) {
        if (entries.size() == 1) {
            return entries.get(0);
        }
        int maxPri = entries.stream().mapToInt(Entry::priority).max().orElse(0);
        var byPri = entries.stream().filter(e -> e.priority() == maxPri).toList();
        if (byPri.size() == 1) {
            return byPri.get(0);
        }
        int minSpec = byPri.stream().mapToInt(Entry::specificity).min().orElse(0);
        var bySpec = byPri.stream().filter(e -> e.specificity() == minSpec).toList();
        if (bySpec.size() == 1) {
            return bySpec.get(0);
        }
        throw new IllegalArgumentException(
                "Ambiguous registration for '" + name + "': " + bySpec.size()
                + " entries with same priority=" + maxPri + " and specificity=" + minSpec);
    }

    // ── 按算法族解析（类型化查询内部使用） ──

    private static Object resolveByRole(AlgorithmKind family, String expression) {
        CheckUtil.notEmpty(expression, "DSL expression cannot be empty");
        try {
            return resolveExpr(DslParser.parse(expression), family);
        } catch (ClassCastException e) {
            // 名字在其它族存在但类型不兼容本族：对本族视为未注册
            throw new UnregisteredAlgorithmException(expression);
        }
    }

    // ── 类型化查询 ──

    public static Digest digest(String expression) {
        return (Digest) resolveByRole(AlgorithmKind.DIGEST, expression);
    }

    public static ExtendedDigest extendedDigest(String expression) {
        return (ExtendedDigest) resolveByRole(AlgorithmKind.EXTENDED_DIGEST, expression);
    }

    public static BlockCipher blockCipher(String expression) {
        return (BlockCipher) resolveByRole(AlgorithmKind.BLOCK_CIPHER, expression);
    }

    public static Mac mac(String expression) {
        return (Mac) resolveByRole(AlgorithmKind.MAC, expression);
    }

    public static AsymmetricBlockCipher asymmetricCipher(String expression) {
        return (AsymmetricBlockCipher) resolveByRole(AlgorithmKind.ASYMMETRIC_BLOCK_CIPHER, expression);
    }

    public static Agreement agreement(String expression) {
        return (Agreement) resolveByRole(AlgorithmKind.AGREEMENT, expression);
    }

    public static JdkKeyPairGenerator keyPairGenerator(String expression) {
        return (JdkKeyPairGenerator) resolveByRole(AlgorithmKind.KEY_PAIR_GENERATOR, expression);
    }

    public static AsymmetricCipherKeyPairGenerator asymmetricKeyPairGenerator(String expression) {
        return (AsymmetricCipherKeyPairGenerator)
                resolveByRole(AlgorithmKind.ASYMMETRIC_KEY_PAIR_GENERATOR, expression);
    }

    public static DerivationFunction derivationFunction(String expression) {
        try {
            return (DerivationFunction) resolveByRole(AlgorithmKind.DERIVATION, expression);
        } catch (UnregisteredAlgorithmException e) {
            return new PlaceholderDerivationFunction();
        }
    }

    public static ExtendableOutputFunction xof(String expression) {
        try {
            return (ExtendableOutputFunction) resolveByRole(AlgorithmKind.XOF, expression);
        } catch (UnregisteredAlgorithmException e) {
            return new PlaceholderXof();
        }
    }

    public static KeyEncapsulationMechanism kem(String expression) {
        try {
            return (KeyEncapsulationMechanism) resolveByRole(AlgorithmKind.KEM, expression);
        } catch (UnregisteredAlgorithmException e) {
            return new PlaceholderKem();
        }
    }

    public static AsymmetricCipher asymmetricStreamCipher(String expression) {
        try {
            return (AsymmetricCipher) resolveByRole(AlgorithmKind.ASYMMETRIC_CIPHER, expression);
        } catch (UnregisteredAlgorithmException e) {
            return new BufferedAsymmetricBlockCipher(asymmetricCipher(expression));
        }
    }

    public static BlockCipherPadding blockCipherPadding(String expression) {
        return (BlockCipherPadding) resolveByRole(AlgorithmKind.BLOCK_CIPHER_PADDING, expression);
    }

    public static EntropySource entropySource(String expression) {
        try {
            return (EntropySource) resolveByRole(AlgorithmKind.ENTROPY_SOURCE, expression);
        } catch (UnregisteredAlgorithmException e) {
            return new SecureRandomEntropySource();
        }
    }

    public static EntropySource entropySource() {
        return entropySource("default");
    }

    public static DeterministicRandomBitGenerator hmacDrbg(String hmacAlgorithm, int securityStrengthBits,
                                        byte[] personalizationString) {
        CheckUtil.notEmpty(hmacAlgorithm, "HMAC algorithm name cannot be empty");
        // 按 HMAC 算法名构造 HMAC_DRBG（支持裸名如 "HmacSHA256" 或 DSL 形式 "HMac(SHA-256)"）。
        // DeterministicRandomBitGenerator 族本身不注册具体实现，故直接以 HMAC 算法解析，避免在类型化查询中
        // 跨族拾取到不兼容类型。
        return new HMacDrbg(mac(hmacAlgorithm), new SecureRandomEntropySource(),
                securityStrengthBits, personalizationString);
    }

    // ── 查询已注册算法名 ──

    /** @return 所有已注册的 DSL 名称（不可变，跨所有算法族合并） */
    public static Set<String> registeredAlgorithms() {
        var result = new java.util.HashSet<String>();
        for (Map<String, List<Entry>> familyMap : ROLES.values()) {
            result.addAll(familyMap.keySet());
        }
        return Collections.unmodifiableSet(result);
    }
}
