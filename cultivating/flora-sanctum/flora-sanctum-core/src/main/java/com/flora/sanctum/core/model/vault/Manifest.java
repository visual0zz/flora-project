package com.flora.sanctum.core.model.vault;
import com.flora.sanctum.core.model.*;

import java.util.Base64;

/**
 * manifest 明文引导块（见设计 02"manifest"）。
 * <p>
 * 负载 JSON：{version, type:"manifest", crypto, kdf, salt, params{memoryKiB,iterations,parallelism}}。
 * 块格式与密文对齐：{@code 信封头 + 负载 + MAC(尾附)}，
 * MAC = HMAC-SHA256(macKey, {@code uuid ‖ 时间戳 ‖ 信封头 ‖ 负载})
 * （见 {@link com.flora.sanctum.core.model.impl.ManifestStore}）。
 * 负载内不含时间戳：块级时间戳存于块前缀（见 MarkdownObjectStore），既参与 AAD/MAC，
 * 也用于冲突仲裁与时钟锚点，但不在 JSON 内部冗余存储。
 * <p>
 * manifest 块的 uuid 为普通随机 uuid（不预留特殊值），定位时通过全局扫描明文块、
 * 按 {@code type=="manifest"} 识别，而非依赖固定路径。
 * <p>
 * 根对象 uuid 为随机生成、不依赖主密码；manifest 不记录它，解锁时由 keyId 路由扫描定位。
 * 换主密码不改变根对象路径（仅根块以新 KEK 重加密）。
 * <p>
 * manifest 额外承载 {@code seed} 字段：仓库级 keyId 派生种子 {@code repoKeyIdSeed} 经 KEK 加密后的
 * blob（base64）。解锁时由 KEK 解密即得种子，无需再从根对象块读取（见 02"解锁流程"重构：seed 进 manifest）。
 * 该字段为可空：未设置（未解锁或种子缺失）时为 null。
 */
public final class Manifest {

    private final int version;
    private final String crypto;
    private final String kdf;
    private final byte[] salt;
    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;
    /** 经 KEK 加密的 repoKeyIdSeed blob（base64 于 JSON）；未设置时为 null。 */
    private final byte[] encryptedSeed;

    public Manifest(int version, String crypto, String kdf, byte[] salt,
                    int memoryKiB, int iterations, int parallelism, byte[] encryptedSeed) {
        this.version = version;
        this.crypto = crypto;
        this.kdf = kdf;
        this.salt = salt;
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
        this.encryptedSeed = encryptedSeed == null ? null : encryptedSeed.clone();
    }

    public int version() {
        return version;
    }

    public String crypto() {
        return crypto;
    }

    public String kdf() {
        return kdf;
    }

    public byte[] salt() {
        return salt.clone();
    }

    public int memoryKiB() {
        return memoryKiB;
    }

    public int iterations() {
        return iterations;
    }

    public int parallelism() {
        return parallelism;
    }

    /** 经 KEK 加密的 repoKeyIdSeed blob；未设置（未解锁或种子缺失）为 null。 */
    public byte[] encryptedSeed() {
        return encryptedSeed == null ? null : encryptedSeed.clone();
    }

    /** manifest MAC 密钥派生：macKey = HKDF-SHA256(KEK, "sanctum-manifest-mac", 32B)（见 02）。 */
    public byte[] manifestMacKey(byte[] kek) {
        return com.flora.sanctum.core.crypto.impl.HkdfSha256.derive(kek, null, "sanctum-manifest-mac", 32);
    }

    /** 从 JSON 负载解析 manifest（MAC 在块尾部，不在此负载内）。 */
    public static Manifest fromJson(byte[] payload) {
        com.flora.root.codec.json.model.JsonObject n = com.flora.root.codec.JsonUtil.parseObject(
                new String(payload, java.nio.charset.StandardCharsets.UTF_8));
        if (StoredNodeType.MANIFEST != StoredNodeType.fromTag(n.getString("type"))) {
            throw new IllegalArgumentException("not a manifest");
        }
        com.flora.root.codec.json.model.JsonObject params = n.getObject("params");
        String seedStr = n.getString("seed");
        byte[] seed = seedStr == null ? null : Base64.getDecoder().decode(seedStr);
        return new Manifest(
                n.getInt("version"),
                n.getString("crypto"),
                n.getString("kdf"),
                Base64.getDecoder().decode(n.getString("salt")),
                params.getInt("memoryKiB"),
                params.getInt("iterations"),
                params.getInt("parallelism"),
                seed
        );
    }
}
