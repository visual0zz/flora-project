package com.flora.crypto.core;

import com.flora.crypto.core.bridge.JdkAgreement;
import com.flora.crypto.core.bridge.JdkAsymmetricBlockCipher;
import com.flora.crypto.core.bridge.JdkAsymmetricKeyPairGenerator;
import com.flora.crypto.core.bridge.JdkBlockCipher;
import com.flora.crypto.core.bridge.JdkDigest;
import com.flora.crypto.core.bridge.JdkKeyPairGenerator;
import com.flora.crypto.core.bridge.JdkKem;
import com.flora.crypto.core.bridge.JdkMac;
import com.flora.crypto.core.bridge.SecureRandomEntropySource;
import com.flora.crypto.core.combinator.BufferedAsymmetricBlockCipher;
import com.flora.crypto.core.impl.AgreementBasedKem;
import com.flora.crypto.core.impl.Argon2;
import com.flora.crypto.core.impl.Blake2bDigest;
import com.flora.crypto.core.impl.BCrypt;
import com.flora.crypto.core.impl.HMac;
import com.flora.crypto.core.impl.HMacDrbg;
import com.flora.crypto.core.impl.Pbkdf2DerivationFunction;
import com.flora.crypto.core.impl.Poly1305Mac;
import com.flora.crypto.core.impl.Ripemd160Digest;
import com.flora.crypto.core.impl.Scrypt;
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
import com.flora.crypto.core.mode.CBCBlockCipher;
import com.flora.crypto.core.mode.CFBBlockCipher;
import com.flora.crypto.core.mode.GCMBlockCipher;
import com.flora.crypto.core.mode.OFBBlockCipher;
import com.flora.crypto.core.mode.SICBlockCipher;
import com.flora.crypto.core.padding.ISO7816d4Padding;
import com.flora.crypto.core.padding.PKCS7Padding;
import com.flora.crypto.core.padding.ZeroBytePadding;
import com.flora.java.CheckUtil;

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
 * <p>注册时指定角色类型（如 {@link Digest}、{@link Mac}），同名算法可安全注册在不同角色下。
 * JDK 适配器与其它算法一样作为普通条目注册于对应角色，不享受特殊回退通道；类型化查询
 * （如 {@link #digest(String)}）优先在本角色查找，未命中则跨所有角色统一解析（DSL 子依赖亦跨角色搜索）。</p>
 * <p><b>组合算法须以 DSL 带参形式调用</b>，例如 {@code "PBKDF2(HMac(SHA-256))"}、{@code "HMac(SHA-256)"}、
 * {@code "CBC(AES)"}。以裸名（无参）查询一个需要参数的组合算法（如 {@code "HMac"}）会抛出
 * {@link IllegalArgumentException}，而不是用空参数数组调用工厂。</p>
 * <pre>{@code
 * CryptoProvider.register(DerivationFunction.class, "PBKDF2", new Class[]{Mac.class},
 *         args -> new Pbkdf2DerivationFunction((Mac) args[0]));
 * CryptoProvider.register(Mac.class, "HMac", new Class[]{ExtendedDigest.class},
 *         args -> new HMac((ExtendedDigest) args[0]));
 * // 查询时 DSL 自动解析依赖：
 * DerivationFunction kdf = CryptoProvider.derivationFunction("PBKDF2(HMac(SHA-256))");
 * }</pre>
 * <p>表达式确实无法解析到任何已注册工厂时抛出 {@link UnregisteredAlgorithmException}；
 * 需要「未注册即兜底」语义的查询方法（如 {@link #derivationFunction(String)}）仅捕获该异常以返回占位实现，
 * 不会掩盖参数缺失 / 类型错误等真正的配置问题。</p>
 */
public final class CryptoProvider {

    private CryptoProvider() {}

    // ── 按角色分类注册表 ──

    /**
     * 注册表条目。
     * <ul>
     *   <li>{@code takesArguments}：工厂是否依赖调用方解析出的参数（组合算法为 true，
     *       JDK 适配器与无参原语为 false）。裸名查询命中 {@code takesArguments=true} 的工厂时视为参数缺失。</li>
     *   <li>{@code paramTypes}：工厂各参数的期望类型，用于在调用前做运行时类型校验（见 {@code applyEntry}）。</li>
     * </ul>
     */
    private record Entry(int priority, int specificity, boolean takesArguments,
                         Class<?>[] paramTypes, Function<Object[], ?> factory) {}

    /** 角色类型 → (DSL 名称 → 候选条目列表)。同名算法在不同角色下互不干扰。 */
    private static final Map<Class<?>, Map<String, List<Entry>>> ROLES = new ConcurrentHashMap<>();

    static {
        // ── JDK 适配器（裸名注册，priority=0）──
        registerJdkAdapters(Digest.class, JdkDigest.SUPPORTED, JdkDigest::of);
        registerJdkAdapters(BlockCipher.class, JdkBlockCipher.SUPPORTED, JdkBlockCipher::of);
        registerJdkAdapters(Mac.class, JdkMac.SUPPORTED, JdkMac::of);
        registerJdkAdapters(AsymmetricBlockCipher.class, Set.of("RSA"), JdkAsymmetricBlockCipher::of);
        registerJdkAdapters(Agreement.class, JdkAgreement.SUPPORTED, JdkAgreement::of);
        registerJdkAdapters(JdkKeyPairGenerator.class, JdkKeyPairGenerator.SUPPORTED, JdkKeyPairGenerator::of);
        registerJdkAdapters(AsymmetricCipherKeyPairGenerator.class,
                JdkAsymmetricKeyPairGenerator.SUPPORTED, JdkAsymmetricKeyPairGenerator::of);
        registerJdkAdapters(KEM.class, AgreementBasedKem.SUPPORTED, AgreementBasedKem::of);

        // ── KDF（依赖其他算法，DSL 自动解析）──
        register(DerivationFunction.class, "KDF2", new Class[]{Digest.class},
                args -> new Kdf2DerivationFunction((Digest) args[0]));
        register(DerivationFunction.class, "PBKDF2", new Class[]{Mac.class},
                args -> new Pbkdf2DerivationFunction((Mac) args[0]));
        register(DerivationFunction.class, "HKDF", new Class[]{Mac.class},
                args -> new HkdfDerivationFunction((Mac) args[0]));

        // ── 纯 Java 摘要实现 ──
        register(Digest.class, "Blake2b", new Class[]{Integer.class},
                args -> new Blake2bDigest((Integer) args[0]));
        register(Digest.class, "BLAKE2B-256", new Class[]{}, args -> new Blake2bDigest(32));
        register(Digest.class, "BLAKE2B-512", new Class[]{}, args -> new Blake2bDigest(64));
        register(Digest.class, "Ripemd160", new Class[]{}, args -> new Ripemd160Digest());
        register(Digest.class, "RIPEMD160", new Class[]{}, args -> new Ripemd160Digest());

        // ── 纯 Java MAC ──
        register(Mac.class, "Poly1305", new Class[]{}, args -> new Poly1305Mac());
        register(Mac.class, "POLY1305", new Class[]{}, args -> new Poly1305Mac());

        // ── 密码哈希 / KDF（盐、迭代、内存等参数经 init 传入）──
        register(DerivationFunction.class, "Argon2", new Class[]{}, args -> new Argon2());
        register(DerivationFunction.class, "ARGON2", new Class[]{}, args -> new Argon2());
        register(DerivationFunction.class, "BCrypt", new Class[]{}, args -> new BCrypt());
        register(DerivationFunction.class, "BCRYPT", new Class[]{}, args -> new BCrypt());
        register(DerivationFunction.class, "Scrypt", new Class[]{}, args -> new Scrypt());
        register(DerivationFunction.class, "SCRYPT", new Class[]{}, args -> new Scrypt());

        // ── JDK KEM 转发（后量子 ML-KEM）──
        registerJdkAdapters(KEM.class, JdkKem.SUPPORTED, JdkKem::of);

        // ── Mac 组合 ──
        register(Mac.class, "HMac", new Class[]{ExtendedDigest.class},
                args -> new HMac((ExtendedDigest) args[0]));

        // ── 填充策略 ──
        register(BlockCipherPadding.class, "PKCS7", new Class[]{}, args -> new PKCS7Padding());
        register(BlockCipherPadding.class, "PKCS5", new Class[]{}, args -> new PKCS7Padding());
        register(BlockCipherPadding.class, "ISO7816", new Class[]{}, args -> new ISO7816d4Padding());
        register(BlockCipherPadding.class, "ISO7816-4", new Class[]{}, args -> new ISO7816d4Padding());
        register(BlockCipherPadding.class, "ZeroByte", new Class[]{}, args -> new ZeroBytePadding());

        // ── 分组密码模式 ──
        register(BlockCipher.class, "CBC", new Class[]{BlockCipher.class},
                args -> new CBCBlockCipher((BlockCipher) args[0]));
        register(BlockCipher.class, "CFB", new Class[]{BlockCipher.class},
                args -> new CFBBlockCipher((BlockCipher) args[0]));
        register(BlockCipher.class, "OFB", new Class[]{BlockCipher.class},
                args -> new OFBBlockCipher((BlockCipher) args[0]));
        register(BlockCipher.class, "CTR", new Class[]{BlockCipher.class},
                args -> new SICBlockCipher((BlockCipher) args[0]));
        register(BlockCipher.class, "GCM", new Class[]{BlockCipher.class},
                args -> new GCMBlockCipher((BlockCipher) args[0]));
    }

    // ── 注册 API ──

    /**
     * 注册一个算法工厂。
     *
     * @param role       角色类型（如 {@code Digest.class}）
     * @param dslName    DSL 名称（如 {@code "PBKDF2"}）
     * @param paramTypes 参数类型列表，用于运行时类型校验
     * @param factory    工厂函数，接收已解析的参数数组
     */
    public static void register(Class<?> role, String dslName, Class<?>[] paramTypes,
                                Function<Object[], ?> factory) {
        register(role, dslName, 0, 1, paramTypes, factory);
    }

    /**
     * 注册一个算法工厂（指定优先级和具体度）。
     *
     * @param role        角色类型
     * @param dslName     DSL 名称
     * @param priority    优先级（越大越优先）
     * @param specificity 具体度（越小越优先，通常为支持算法数的倒数）
     * @param paramTypes  参数类型列表，用于在调用前做运行时类型校验；
     *                    非空数组表示工厂依赖参数（即该算法须以 {@code name(args...)} 形式调用）
     * @param factory     工厂函数，接收已解析的参数数组
     */
    public static void register(Class<?> role, String dslName, int priority, int specificity,
                                Class<?>[] paramTypes,
                                Function<Object[], ?> factory) {
        CheckUtil.notEmpty(dslName, "DSL name cannot be empty");
        boolean takesArguments = paramTypes != null && paramTypes.length > 0;
        register(role, dslName, priority, specificity, takesArguments, paramTypes, factory);
    }

    /** 内部注册入口，显式控制 {@code takesArguments}（JDK 适配器忽略参数，故为 false）。 */
    private static void register(Class<?> role, String dslName, int priority, int specificity,
                                 boolean takesArguments, Class<?>[] paramTypes,
                                 Function<Object[], ?> factory) {
        CheckUtil.notEmpty(dslName, "DSL name cannot be empty");
        ROLES.computeIfAbsent(role, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(dslName, k -> new CopyOnWriteArrayList<>())
                .add(new Entry(priority, specificity, takesArguments,
                        paramTypes == null ? new Class<?>[0] : paramTypes, factory));
    }

    /** 批量注册 JDK 适配器的所有支持算法名到指定角色（作为普通算法，忽略调用方参数）。 */
    private static void registerJdkAdapters(Class<?> role, Set<String> names,
                                            Function<String, ?> factory) {
        int specificity = names.size();
        for (String name : names) {
            register(role, name, 0, specificity, false, new Class<?>[0],
                    args -> factory.apply(name));
        }
    }

    // ── DSL 解析与裁决 ──

    /**
     * 解析 DSL 表达式并返回构造好的实例（跨所有角色搜索）。
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
     * @param parsed   DslParser 的解析结果（String / Invocation / 字面量）
     * @param hintRole 提示角色（可为 null），优先在该角色下查找
     */
    private static Object resolveExpr(Object parsed, Class<?> hintRole) {
        if (parsed instanceof String bareName) {
            return resolveName(bareName, hintRole, new Object[0]);
        }
        if (parsed instanceof DslParser.Invocation(String name, Object[] args1)) {
            Object[] args = resolveArgs(args1);
            return resolveName(name, hintRole, args);
        }
        // 字面量直接返回
        return parsed;
    }

    private static Object resolveName(String name, Class<?> hintRole, Object[] args) {
        // 1. 提示角色优先
        if (hintRole != null) {
            Entry entry = findEntry(hintRole, name);
            if (entry != null) return applyEntry(entry, name, args);
        }
        // 2. 跨角色搜索（JDK 适配器与组合算法均为普通条目，统一在此解析）
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

    private static Entry findEntry(Class<?> role, String name) {
        Map<String, List<Entry>> roleMap = ROLES.get(role);
        if (roleMap == null) return null;
        List<Entry> entries = roleMap.get(name);
        if (entries == null || entries.isEmpty()) return null;
        return select(entries, name);
    }

    private static Entry findAcrossRoles(String name) {
        Entry found = null;
        for (Map<String, List<Entry>> roleMap : ROLES.values()) {
            List<Entry> entries = roleMap.get(name);
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

    // ── 按角色解析（类型化查询内部使用） ──

    private static Object resolveByRole(Class<?> role, String expression) {
        CheckUtil.notEmpty(expression, "DSL expression cannot be empty");
        try {
            return resolveExpr(DslParser.parse(expression), role);
        } catch (ClassCastException e) {
            // 名字在其它角色存在但类型不兼容本角色：对本角色视为未注册
            throw new UnregisteredAlgorithmException(expression);
        }
    }

    // ── 类型化查询 ──

    public static Digest digest(String expression) {
        return (Digest) resolveByRole(Digest.class, expression);
    }

    public static ExtendedDigest extendedDigest(String expression) {
        return (ExtendedDigest) resolveByRole(ExtendedDigest.class, expression);
    }

    public static BlockCipher blockCipher(String expression) {
        return (BlockCipher) resolveByRole(BlockCipher.class, expression);
    }

    public static Mac mac(String expression) {
        return (Mac) resolveByRole(Mac.class, expression);
    }

    public static AsymmetricBlockCipher asymmetricCipher(String expression) {
        return (AsymmetricBlockCipher) resolveByRole(AsymmetricBlockCipher.class, expression);
    }

    public static Agreement agreement(String expression) {
        return (Agreement) resolveByRole(Agreement.class, expression);
    }

    public static JdkKeyPairGenerator keyPairGenerator(String expression) {
        return (JdkKeyPairGenerator) resolveByRole(JdkKeyPairGenerator.class, expression);
    }

    public static AsymmetricCipherKeyPairGenerator asymmetricKeyPairGenerator(String expression) {
        return (AsymmetricCipherKeyPairGenerator)
                resolveByRole(AsymmetricCipherKeyPairGenerator.class, expression);
    }

    public static DerivationFunction derivationFunction(String expression) {
        try {
            return (DerivationFunction) resolveByRole(DerivationFunction.class, expression);
        } catch (UnregisteredAlgorithmException e) {
            return new PlaceholderDerivationFunction();
        }
    }

    public static Xof xof(String expression) {
        try {
            return (Xof) resolveByRole(Xof.class, expression);
        } catch (UnregisteredAlgorithmException e) {
            return new PlaceholderXof();
        }
    }

    public static KEM kem(String expression) {
        try {
            return (KEM) resolveByRole(KEM.class, expression);
        } catch (UnregisteredAlgorithmException e) {
            return new PlaceholderKem();
        }
    }

    public static AsymmetricCipher asymmetricStreamCipher(String expression) {
        try {
            return (AsymmetricCipher) resolveByRole(AsymmetricCipher.class, expression);
        } catch (UnregisteredAlgorithmException e) {
            return new BufferedAsymmetricBlockCipher(asymmetricCipher(expression));
        }
    }

    public static BlockCipherPadding blockCipherPadding(String expression) {
        return (BlockCipherPadding) resolveByRole(BlockCipherPadding.class, expression);
    }

    public static EntropySource entropySource(String expression) {
        try {
            return (EntropySource) resolveByRole(EntropySource.class, expression);
        } catch (UnregisteredAlgorithmException e) {
            return new SecureRandomEntropySource();
        }
    }

    public static EntropySource entropySource() {
        return entropySource("default");
    }

    public static SP80090DRBG hmacDrbg(String hmacAlgorithm, int securityStrengthBits,
                                        byte[] personalizationString) {
        CheckUtil.notEmpty(hmacAlgorithm, "HMAC algorithm name cannot be empty");
        // 按 HMAC 算法名构造 HMAC_DRBG（支持裸名如 "HmacSHA256" 或 DSL 形式 "HMac(SHA-256)"）。
        // SP80090DRBG 角色本身不注册具体实现，故直接以 HMAC 算法解析，避免在类型化查询中
        // 跨角色拾取到不兼容类型。
        return new HMacDrbg(mac(hmacAlgorithm), new SecureRandomEntropySource(),
                securityStrengthBits, personalizationString);
    }

    // ── 查询已注册算法名 ──

    /** @return 所有已注册的 DSL 名称（不可变，跨所有角色合并） */
    public static Set<String> registeredAlgorithms() {
        var result = new java.util.HashSet<String>();
        for (Map<String, List<Entry>> roleMap : ROLES.values()) {
            result.addAll(roleMap.keySet());
        }
        return Collections.unmodifiableSet(result);
    }
}
