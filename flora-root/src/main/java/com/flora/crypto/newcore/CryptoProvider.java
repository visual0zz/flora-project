package com.flora.crypto.newcore;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmConstant;
import com.flora.common.algorithm.AlgorithmFamily;
import com.flora.common.algorithm.UnregisteredAlgorithmException;
import com.flora.crypto.newcore.impl.DslParser;
import com.flora.crypto.newcore.interfaces.algorithm.Agreement;
import com.flora.crypto.newcore.interfaces.algorithm.AsymmetricBlockCipher;
import com.flora.crypto.newcore.interfaces.algorithm.AsymmetricCipher;
import com.flora.crypto.newcore.interfaces.algorithm.AsymmetricCipherKeyPairGenerator;
import com.flora.crypto.newcore.interfaces.algorithm.AsymmetricScheme;
import com.flora.crypto.newcore.interfaces.algorithm.AuthenticatedEncryptionWithAssociatedDataBlockCipher;
import com.flora.crypto.newcore.interfaces.algorithm.BlockCipher;
import com.flora.crypto.newcore.interfaces.algorithm.DerivationFunction;
import com.flora.crypto.newcore.interfaces.algorithm.DeterministicRandomBitGenerator;
import com.flora.crypto.newcore.interfaces.algorithm.Digest;
import com.flora.crypto.newcore.interfaces.algorithm.EntropySource;
import com.flora.crypto.newcore.interfaces.algorithm.ExtendableOutputFunction;
import com.flora.crypto.newcore.interfaces.algorithm.KeyEncapsulationMechanism;
import com.flora.crypto.newcore.interfaces.algorithm.Mac;
import com.flora.crypto.newcore.interfaces.algorithm.MaskGenerationFunction;
import com.flora.crypto.newcore.interfaces.algorithm.Padding;
import com.flora.crypto.newcore.interfaces.algorithm.Signature;
import com.flora.java.CheckUtil;
import com.flora.tag.ModuleEntry;

/**
 * 加密组件注册表（DSL 表达式驱动，语法与旧 core 一致）。
 * <p>算法组件通过 DSL 表达式注册和解析：</p>
 * <pre>
 * 表达式 = 裸名 | 裸名(表达式, ...) | 字面量
 * 字面量 = integer:数字 | float:小数 | string:文本 | bytes:十六进制
 * </pre>
 * <p>注册委托给 {@link CryptoAlgorithmFamilyRegister}（复用 common 的注册 / 归属校验 / 同名裁决 /
 * 按名查询能力）：算法族通过 {@link AlgorithmFamily#registerTo()} 自述注册到
 * {@link CryptoAlgorithmFamilyRegister}，经本类登记。每个算法名全局唯一，由裁决后胜出的唯一
 * {@link AlgorithmFamily} 负责生产实例（{@link AlgorithmFamily#construct}）。</p>
 * <p>组合算法以 DSL 带参形式调用，例如 {@code "CBC(AES)"}、{@code "HMac(SHA-256)"}。
 * 参数中的算法实例（{@link AlgorithmComponent}）直接注入；字面量参数包装为 {@link AlgorithmConstant}。
 * 表达式无法解析到任何已注册算法时抛出 {@link UnregisteredAlgorithmException}（common 版）。</p>
 */
@ModuleEntry
public final class CryptoProvider {

    /** 注册中心：每个实例即一个独立注册表。 */
    private static final CryptoAlgorithmFamilyRegister REGISTRY = new CryptoAlgorithmFamilyRegister();

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

    // ── 注册 API ──

    /**
     * 注册一个算法族（其 {@code supportedAlgorithms()} 中的每个名字分别登记）。
     *
     * @param factory 算法族实例，须已通过 {@link AlgorithmFamily#registerTo()} 自述注册到
     *                {@link CryptoAlgorithmFamilyRegister}
     */
    public static void register(AlgorithmFamily<?> factory) {
        REGISTRY.register(factory);
    }

    /** 通过 SPI 自动发现并注册所有自述为 {@link CryptoAlgorithmFamilyRegister} 的算法族。 */
    public static void registerBySpi() {
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
        AlgorithmFamily<?> family = REGISTRY.get(name, AlgorithmFamily.class);
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

    public static BlockCipher blockCipher(String expression) {
        return cast(BlockCipher.class, expression);
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

    public static AuthenticatedEncryptionWithAssociatedDataBlockCipher aeadBlockCipher(String expression) {
        return cast(AuthenticatedEncryptionWithAssociatedDataBlockCipher.class, expression);
    }

    private static <T> T cast(Class<T> type, String expression) {
        Object resolved = resolve(expression);
        if (type.isInstance(resolved)) {
            return type.cast(resolved);
        }
        throw new UnregisteredAlgorithmException(expression);
    }
}
