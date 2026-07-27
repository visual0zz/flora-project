package com.flora.crypto.core;
import com.flora.crypto.core.engine.JdkDigest;
import com.flora.crypto.core.engine.JdkBlockCipher;
import com.flora.crypto.core.engine.JdkStreamCipher;
import com.flora.crypto.core.engine.JdkAsymmetricBlockCipher;
import com.flora.crypto.core.engine.JdkMac;
import com.flora.crypto.core.engine.JdkSigner;
import com.flora.crypto.core.engine.JdkKeyPairGenerator;

import com.flora.java.CheckUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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
 *
 * <h2>自定义算法注册</h2>
 * 除 JDK 自带算法外，可把实现了 {@code core} 角色接口的自定义算法注册进来，按名优先加载：
 *
 * <pre>{@code
 * CryptoProvider.registerDigest("MyHash", MyDigest::new);
 * Digest d = CryptoProvider.digest("MyHash");   // 返回 MyDigest，而非 JDK 适配器
 * }</pre>
 *
 * 查找顺序为：<b>自定义注册表优先，未命中再回退到 JDK 适配器</b>。因此注册同名算法（如
 * {@code "SHA-256"}）即可覆盖 JDK 默认实现。注册发生在 JVM 全局，请尽早（如启动时）完成。
 */
public final class CryptoProvider {

    private CryptoProvider() {
    }

    // ── 自定义注册表：每个角色一张，按名字优先命中 ──
    private static final Map<String, Supplier<? extends Digest>> DIGEST_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends BlockCipher>> BLOCK_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends StreamCipher>> STREAM_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends AsymmetricBlockCipher>> ASYM_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends Mac>> MAC_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends Signer>> SIGNER_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, Supplier<? extends JdkKeyPairGenerator>> KPG_REGISTRY = new ConcurrentHashMap<>();

    // ── 注册入口 ──

    public static void registerDigest(String name, Supplier<? extends Digest> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        DIGEST_REGISTRY.put(name, factory);
    }

    public static void registerBlockCipher(String name, Supplier<? extends BlockCipher> factory) {
        CheckUtil.notEmpty(name, "变换字符串不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        BLOCK_REGISTRY.put(name, factory);
    }

    public static void registerStreamCipher(String name, Supplier<? extends StreamCipher> factory) {
        CheckUtil.notEmpty(name, "变换字符串不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        STREAM_REGISTRY.put(name, factory);
    }

    public static void registerAsymmetricCipher(String name, Supplier<? extends AsymmetricBlockCipher> factory) {
        CheckUtil.notEmpty(name, "变换字符串不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        ASYM_REGISTRY.put(name, factory);
    }

    public static void registerMac(String name, Supplier<? extends Mac> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        MAC_REGISTRY.put(name, factory);
    }

    public static void registerSigner(String name, Supplier<? extends Signer> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        SIGNER_REGISTRY.put(name, factory);
    }

    public static void registerKeyPairGenerator(String name, Supplier<? extends JdkKeyPairGenerator> factory) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factory, "工厂不能为空");
        KPG_REGISTRY.put(name, factory);
    }

    // ── 查询入口：注册表优先，未命中回退 JDK 适配器 ──

    public static Digest digest(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends Digest> f = DIGEST_REGISTRY.get(name);
        return f != null ? f.get() : JdkDigest.of(name);
    }

    public static BlockCipher blockCipher(String transformation) {
        CheckUtil.notEmpty(transformation, "变换字符串不能为空");
        Supplier<? extends BlockCipher> f = BLOCK_REGISTRY.get(transformation);
        return f != null ? f.get() : JdkBlockCipher.of(transformation);
    }

    public static StreamCipher streamCipher(String transformation) {
        CheckUtil.notEmpty(transformation, "变换字符串不能为空");
        Supplier<? extends StreamCipher> f = STREAM_REGISTRY.get(transformation);
        return f != null ? f.get() : JdkStreamCipher.of(transformation);
    }

    public static AsymmetricBlockCipher asymmetricCipher(String name) {
        CheckUtil.notEmpty(name, "变换字符串不能为空");
        Supplier<? extends AsymmetricBlockCipher> f = ASYM_REGISTRY.get(name);
        return f != null ? f.get() : JdkAsymmetricBlockCipher.of(name);
    }

    public static Mac mac(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends Mac> f = MAC_REGISTRY.get(name);
        return f != null ? f.get() : JdkMac.of(name);
    }

    public static Signer signer(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends Signer> f = SIGNER_REGISTRY.get(name);
        return f != null ? f.get() : JdkSigner.of(name);
    }

    public static JdkKeyPairGenerator keyPairGenerator(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        Supplier<? extends JdkKeyPairGenerator> f = KPG_REGISTRY.get(name);
        return f != null ? f.get() : JdkKeyPairGenerator.of(name);
    }
}
