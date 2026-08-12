package com.flora.root.crypto.core;

import com.flora.root.common.register.*;
import com.flora.root.common.register.AlgorithmComponent;
import com.flora.root.common.register.AlgorithmConstant;
import com.flora.root.common.register.AlgorithmFactory;
import com.flora.root.common.register.UnregisteredAlgorithmException;
import com.flora.root.crypto.core.combinator.BufferedAsymmetricBlockCipher;
import com.flora.root.crypto.core.combinator.PaddedAsymmetricBlockCipher;
import com.flora.root.crypto.core.impl.DslParser;
import com.flora.root.crypto.core.interfaces.algorithm.Agreement;
import com.flora.root.crypto.core.interfaces.algorithm.AsymmetricBlockCipher;
import com.flora.root.crypto.core.interfaces.algorithm.AsymmetricCipher;
import com.flora.root.crypto.core.interfaces.algorithm.AsymmetricCipherKeyPairGenerator;
import com.flora.root.crypto.core.interfaces.algorithm.AsymmetricScheme;
import com.flora.root.crypto.core.interfaces.algorithm.AEADBlockCipher;
import com.flora.root.crypto.core.interfaces.algorithm.BufferedBlockCipher;
import com.flora.root.crypto.core.interfaces.algorithm.LinkedBlockCipher;
import com.flora.root.crypto.core.interfaces.algorithm.DerivationFunction;
import com.flora.root.crypto.core.interfaces.algorithm.DeterministicRandomBitGenerator;
import com.flora.root.crypto.core.interfaces.algorithm.Digest;
import com.flora.root.crypto.core.interfaces.algorithm.EntropySource;
import com.flora.root.crypto.core.interfaces.algorithm.ExtendableOutputFunction;
import com.flora.root.crypto.core.interfaces.algorithm.KeyEncapsulationMechanism;
import com.flora.root.crypto.core.interfaces.algorithm.Mac;
import com.flora.root.crypto.core.interfaces.algorithm.MaskGenerationFunction;
import com.flora.root.crypto.core.interfaces.algorithm.Padding;
import com.flora.root.crypto.core.interfaces.algorithm.Signature;
import com.flora.root.crypto.core.link.CBCBlockCipher;
import com.flora.root.crypto.core.link.CFBBlockCipher;
import com.flora.root.crypto.core.link.GCMBlockCipher;
import com.flora.root.crypto.core.link.OFBBlockCipher;
import com.flora.root.crypto.core.link.SICBlockCipher;
import com.flora.root.crypto.core.bridge.JdkAgreement;
import com.flora.root.crypto.core.bridge.JdkAsymmetricBlockCipher;
import com.flora.root.crypto.core.bridge.JdkAsymmetricKeyPairGenerator;
import com.flora.root.crypto.core.bridge.JdkBlockCipher;
import com.flora.root.crypto.core.bridge.JdkDigest;
import com.flora.root.crypto.core.bridge.JdkKem;
import com.flora.root.crypto.core.bridge.JdkMac;
import com.flora.root.crypto.core.bridge.JdkSignature;
import com.flora.root.crypto.core.bridge.SecureRandomEntropySource;
import com.flora.root.crypto.core.impl.AgreementBasedKem;
import com.flora.root.crypto.core.impl.Argon2;
import com.flora.root.crypto.core.impl.BCrypt;
import com.flora.root.crypto.core.impl.Blake2bDigest;
import com.flora.root.crypto.core.impl.ChaCha20Poly1305;
import com.flora.root.crypto.core.impl.HMac;
import com.flora.root.crypto.core.impl.HkdfDerivationFunction;
import com.flora.root.crypto.core.impl.HMacDrbg;
import com.flora.root.crypto.core.impl.Kdf2DerivationFunction;
import com.flora.root.crypto.core.impl.Pbkdf2DerivationFunction;
import com.flora.root.crypto.core.impl.PlaceholderDerivationFunction;
import com.flora.root.crypto.core.impl.PlaceholderKem;
import com.flora.root.crypto.core.impl.PlaceholderXof;
import com.flora.root.crypto.core.impl.Poly1305Mac;
import com.flora.root.crypto.core.impl.Ripemd160Digest;
import com.flora.root.crypto.core.impl.Scrypt;
import com.flora.root.crypto.core.wrapper.BufferedBlockCipherWrapper;
import com.flora.root.crypto.core.wrapper.PaddedBufferedBlockCipherWrapper;
import com.flora.root.crypto.core.padding.ISO7816d4Padding;
import com.flora.root.crypto.core.padding.Mgf1Generator;
import com.flora.root.crypto.core.padding.OAEPPadding;
import com.flora.root.crypto.core.padding.PKCS1v15Padding;
import com.flora.root.crypto.core.padding.PKCS7Padding;
import com.flora.root.crypto.core.padding.ZeroBytePadding;
import com.flora.root.java.CheckUtil;
import com.flora.root.tag.ModuleEntry;

/**
 * 加密组件注册表（DSL 表达式驱动，语法与旧 core 一致）。
 * <p>算法组件通过 DSL 表达式注册和解析：</p>
 * <pre>
 * 表达式 = 裸名 | 裸名(表达式, ...) | 字面量
 * 字面量 = integer:数字 | float:小数 | string:文本 | bytes:十六进制
 * </pre>
 * <p>注册委托给 {@link CryptoAlgorithmFactoryRegister}（复用 common 的注册 / 归属校验 / 同名裁决 /
 * 按名查询能力）：算法族通过 {@link AlgorithmFactory#registerTo()} 自述注册到
 * {@link CryptoAlgorithmFactoryRegister}，经本类登记。每个算法名全局唯一，由裁决后胜出的唯一
 * {@link AlgorithmFactory} 负责生产实例（{@link AlgorithmFactory#construct}）。
 * 本类静态初始化时注册当前内置的全部算法族，并经 SPI（{@code ServiceLoader}）自动发现其它算法族。</p>
 * <p>组合算法以 DSL 带参形式调用，例如 {@code "CBC(AES)"}、{@code "HMac(SHA-256)"}。
 * 参数中的算法实例（{@link AlgorithmComponent}）直接注入；字面量参数包装为 {@link AlgorithmConstant}。
 * 表达式无法解析到任何已注册算法时抛出 {@link UnregisteredAlgorithmException}（common 版）。</p>
 */
@ModuleEntry
public final class CryptoProvider {

    /** 注册中心：每个实例即一个独立注册表。 */
    private static final CryptoAlgorithmFactoryRegister REGISTRY = new CryptoAlgorithmFactoryRegister();

    /** 字面量参数的常量组件包装。 */
    private record ConstantImpl<T>(T value, Class<T> type) implements AlgorithmConstant<T> {
        @Override
        public T getValue() {
            return value;
        }

        @Override
        public Class<T> getType() {
            return type;
        }
    }

    private CryptoProvider() {}

    static {
        // ── 内置算法族（当前 newcore 已实现的全部算法）──
        // 分组密码模式
        REGISTRY.register(CBCBlockCipher.FACTORY);
        REGISTRY.register(CFBBlockCipher.FACTORY);
        REGISTRY.register(OFBBlockCipher.FACTORY);
        REGISTRY.register(SICBlockCipher.FACTORY);
        REGISTRY.register(GCMBlockCipher.FACTORY);
        // JDK 裸原语桥接（DSL 裸名入口：AES / SHA-256 / HmacSHA256 等）
        REGISTRY.register(JdkBlockCipher.FACTORY);
        REGISTRY.register(JdkDigest.FACTORY);
        REGISTRY.register(JdkMac.FACTORY);
        // JDK 非对称桥接（Agreement / 非对称分组密码 / 密钥对生成 / KEM / 签名 / 熵源）
        REGISTRY.register(JdkAgreement.FACTORY);
        REGISTRY.register(JdkAsymmetricBlockCipher.FACTORY);
        REGISTRY.register(JdkAsymmetricKeyPairGenerator.FACTORY);
        REGISTRY.register(JdkKem.FACTORY);
        REGISTRY.register(JdkSignature.FACTORY);
        REGISTRY.register(SecureRandomEntropySource.FACTORY);
        // 确定性随机比特生成器（SP800-90A HMAC_DRBG）
        REGISTRY.register(HMacDrbg.FACTORY);
        // 纯 Java 摘要 / MAC 原语
        REGISTRY.register(Blake2bDigest.FACTORY);
        REGISTRY.register(Ripemd160Digest.FACTORY);
        REGISTRY.register(Poly1305Mac.FACTORY);
        REGISTRY.register(HMac.FACTORY);
        // 密钥派生 / 口令哈希
        REGISTRY.register(Pbkdf2DerivationFunction.FACTORY);
        REGISTRY.register(Kdf2DerivationFunction.FACTORY);
        REGISTRY.register(HkdfDerivationFunction.FACTORY);
        REGISTRY.register(Scrypt.FACTORY);
        REGISTRY.register(BCrypt.FACTORY);
        REGISTRY.register(Argon2.FACTORY);
        // AEAD 与流式密码原语
        REGISTRY.register(ChaCha20Poly1305.FACTORY);
        // 占位实现（未接入真实引擎的算法兜底）
        REGISTRY.register(PlaceholderDerivationFunction.FACTORY);
        REGISTRY.register(PlaceholderKem.FACTORY);
        REGISTRY.register(PlaceholderXof.FACTORY);
        // 基于 JDK 密钥协商的 KEM 封装（ECDH / X25519 / X448 / DH）
        REGISTRY.register(AgreementBasedKem.FACTORY);
        // 对称填充
        REGISTRY.register(PKCS7Padding.FACTORY);
        REGISTRY.register(ISO7816d4Padding.FACTORY);
        REGISTRY.register(ZeroBytePadding.FACTORY);
        // 非对称编码方案与掩码生成
        REGISTRY.register(Mgf1Generator.FACTORY);
        REGISTRY.register(OAEPPadding.FACTORY);
        REGISTRY.register(PKCS1v15Padding.FACTORY);
        // 组合器
        REGISTRY.register(BufferedAsymmetricBlockCipher.FACTORY);
        REGISTRY.register(PaddedAsymmetricBlockCipher.FACTORY);
        // 原语层缓冲组合器（整段处理，非模式层）
        REGISTRY.register(BufferedBlockCipherWrapper.FACTORY);
        REGISTRY.register(PaddedBufferedBlockCipherWrapper.FACTORY);
        // SPI 自动发现：其它模块（如后续实现模块）经 ServiceLoader 注册的算法族
        REGISTRY.registerBySpi();
    }

    // ── DSL 解析 ──

    /**
     * 解析 DSL 表达式并返回构造好的实例（按名字在注册中心查找算法族并调用其 {@code construct}）。
     *
     * @param expression DSL 表达式，如 {@code "CBC(AES)"}
     * @return 构造好的算法实例
     * @throws UnregisteredAlgorithmException 表达式无法解析到任何已注册算法
     */
    public static Object resolve(String expression) {
        CheckUtil.notEmpty(expression, "DSL expression cannot be empty");
        return resolveExpr(DslParser.parse(expression));
    }

    private static Object resolveExpr(Object parsed) {
        if (parsed instanceof String bareName) {
            return construct(bareName, new AlgorithmComponent[0]);
        }
        if (parsed instanceof DslParser.Invocation(String name, Object[] rawArgs)) {
            Object[] args = resolveArgs(rawArgs);
            AlgorithmComponent[] components = toComponents(args);
            return construct(name, components);
        }
        // 字面量直接返回
        return parsed;
    }

    private static Object construct(String name, AlgorithmComponent[] components) {
        AlgorithmFactory<?> family = REGISTRY.get(name, AlgorithmFactory.class);
        return family.construct(name, components);
    }

    private static Object[] resolveArgs(Object[] rawArgs) {
        Object[] resolved = new Object[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            resolved[i] = resolveExpr(rawArgs[i]);
        }
        return resolved;
    }

    /** 算法实例直接作为组件；字面量包装为 {@link AlgorithmConstant}。 */
    private static AlgorithmComponent[] toComponents(Object[] args) {
        AlgorithmComponent[] components = new AlgorithmComponent[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof AlgorithmComponent c) {
                components[i] = c;
            } else {
                @SuppressWarnings({"unchecked", "rawtypes"})
                AlgorithmConstant<?> constant = new ConstantImpl(arg, (Class) arg.getClass());
                components[i] = constant;
            }
        }
        return components;
    }

    // ── 类型化查询 ──

    public static Digest digest(String expression) {
        return cast(Digest.class, expression);
    }

    public static LinkedBlockCipher blockCipher(String expression) {
        return cast(LinkedBlockCipher.class, expression);
    }

    public static BufferedBlockCipher bufferedBlockCipher(String expression) {
        return cast(BufferedBlockCipher.class, expression);
    }

    public static Mac mac(String expression) {
        return cast(Mac.class, expression);
    }

    public static AsymmetricBlockCipher asymmetricBlockCipher(String expression) {
        return cast(AsymmetricBlockCipher.class, expression);
    }

    public static Agreement agreement(String expression) {
        return cast(Agreement.class, expression);
    }

    public static AsymmetricCipherKeyPairGenerator asymmetricKeyPairGenerator(String expression) {
        return cast(AsymmetricCipherKeyPairGenerator.class, expression);
    }

    public static DerivationFunction derivationFunction(String expression) {
        return cast(DerivationFunction.class, expression);
    }

    public static ExtendableOutputFunction xof(String expression) {
        return cast(ExtendableOutputFunction.class, expression);
    }

    public static KeyEncapsulationMechanism kem(String expression) {
        return cast(KeyEncapsulationMechanism.class, expression);
    }

    public static AsymmetricCipher asymmetricStreamCipher(String expression) {
        return cast(AsymmetricCipher.class, expression);
    }

    public static Padding padding(String expression) {
        return cast(Padding.class, expression);
    }

    public static AsymmetricScheme asymmetricScheme(String expression) {
        return cast(AsymmetricScheme.class, expression);
    }

    public static MaskGenerationFunction maskGenerationFunction(String expression) {
        return cast(MaskGenerationFunction.class, expression);
    }

    public static EntropySource entropySource(String expression) {
        return cast(EntropySource.class, expression);
    }

    public static DeterministicRandomBitGenerator drbg(String expression) {
        return cast(DeterministicRandomBitGenerator.class, expression);
    }

    public static Signature signature(String expression) {
        return cast(Signature.class, expression);
    }

    public static AEADBlockCipher aeadBlockCipher(String expression) {
        return cast(AEADBlockCipher.class, expression);
    }

    private static <T> T cast(Class<T> type, String expression) {
        Object resolved = resolve(expression);
        if (type.isInstance(resolved)) {
            return type.cast(resolved);
        }
        throw new UnregisteredAlgorithmException(expression);
    }
}
