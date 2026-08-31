package com.flora.sanctum.core.model.impl;
import com.flora.sanctum.core.model.*;
import com.flora.sanctum.core.model.vault.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.core.crypto.impl.CipherCodec;
import com.flora.sanctum.core.crypto.impl.Envelope;
import com.flora.sanctum.core.crypto.impl.SecureRandomSource;
import com.flora.sanctum.core.store.Block;
import com.flora.sanctum.core.store.ObjectStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/**
 * manifest 明文引导块读写（收敛 VaultCreator / Sanctum.close / MasterKeyRotator 三处手拼逻辑）。
 * <p>
 * 块格式与密文对齐：{@code 信封头 + JSON 负载 + MAC(尾附)}，
 * MAC = HMAC-SHA256(macKey, {@code uuid ‖ 时间戳 ‖ 信封头 ‖ 负载})，不存于 JSON 内部。
 * 信封头为 {@code magic+version+flags}（不含 uuid）。manifest 块使用普通随机 uuid
 * （无特殊预留值），因此定位时遍历全部明文块、按负载 {@code type=="manifest"} 识别，
 * 而非依赖固定路径。写：复用既有 manifest 块的 uuid（覆盖更新）或生成新的随机 uuid
 * （首次创建）。
 */
public final class ManifestStore {

    private final ObjectStore store;
    private final SecureRandomSource random;

    public ManifestStore(ObjectStore store, SecureRandomSource random) {
        this.store = store;
        this.random = random;
    }

    // ---- 块构造 / 解析（静态工具，供 VaultCreator / VaultUnlocker 复用） ----

    /** 构造明文块信封头：magic+version+flags（PLAINTEXT_HEADER_LEN 字节，不含 uuid）。 */
    public static byte[] plaintextHeader() {
        byte[] header = new byte[Envelope.PLAINTEXT_HEADER_LEN];
        System.arraycopy(Envelope.MAGIC, 0, header, 0, Envelope.MAGIC_LEN);
        header[Envelope.MAGIC_LEN] = Envelope.VERSION_1;
        header[Envelope.MAGIC_LEN + 1] = Envelope.FLAG_PLAINTEXT;
        return header;
    }

    /** MAC 输入：uuid(16B) ‖ 时间戳(ASCII 原文) ‖ 信封头 ‖ 负载。 */
    public static byte[] macInput(byte[] uuidBytes, byte[] header, String timestamp, byte[] payload) {
        byte[] ts = timestamp.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[uuidBytes.length + ts.length + header.length + payload.length];
        System.arraycopy(uuidBytes, 0, out, 0, uuidBytes.length);
        System.arraycopy(ts, 0, out, uuidBytes.length, ts.length);
        System.arraycopy(header, 0, out, uuidBytes.length + ts.length, header.length);
        System.arraycopy(payload, 0, out, uuidBytes.length + ts.length + header.length, payload.length);
        return out;
    }

    /** 计算 manifest MAC：HMAC-SHA256(macKey, uuid ‖ 时间戳(ASCII 原文) ‖ 信封头 ‖ 负载)。 */
    public static byte[] computeMac(byte[] uuidBytes, byte[] macKey, byte[] header, String timestamp, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
            return mac.doFinal(macInput(uuidBytes, header, timestamp, payload));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 构造完整明文块：header + payload + mac（信封原始字节，无异或混淆）。 */
    public static byte[] buildBlock(byte[] uuidBytes, byte[] payload, String timestamp, byte[] macKey) {
        byte[] header = plaintextHeader();
        byte[] mac = computeMac(uuidBytes, macKey, header, timestamp, payload);
        byte[] block = new byte[header.length + payload.length + mac.length];
        System.arraycopy(header, 0, block, 0, header.length);
        System.arraycopy(payload, 0, block, header.length, payload.length);
        System.arraycopy(mac, 0, block, header.length + payload.length, mac.length);
        return block;
    }

    /** 从完整明文块提取负载（去头去尾 MAC）。 */
    public static byte[] payloadOf(byte[] full) {
        return Arrays.copyOfRange(full, Envelope.PLAINTEXT_HEADER_LEN,
                full.length - Envelope.MANIFEST_MAC_LEN);
    }

    /** 从完整明文块提取尾附 MAC。 */
    public static byte[] macOf(byte[] full) {
        return Arrays.copyOfRange(full, full.length - Envelope.MANIFEST_MAC_LEN, full.length);
    }

    /** 从完整明文块提取信封头。 */
    public static byte[] headerOf(byte[] full) {
        return Arrays.copyOfRange(full, 0, Envelope.PLAINTEXT_HEADER_LEN);
    }

    /** 校验完整明文块 MAC（uuid+header+timestamp(ASCII 原文)+payload 与尾附 MAC 比对）。 */
    public static boolean verifyMac(byte[] uuidBytes, byte[] full, String timestamp, byte[] macKey) {
        byte[] expected = computeMac(uuidBytes, macKey, headerOf(full), timestamp, payloadOf(full));
        return MessageDigestIsEqual(expected, macOf(full));
    }

    private static boolean MessageDigestIsEqual(byte[] a, byte[] b) {
        return java.security.MessageDigest.isEqual(a, b);
    }

    // ---- 实例读写 ----

    /**
     * 读取 manifest；不存在返回 null。
     * <p>定位：扫描全部明文块，按负载 {@code type=="manifest"} 识别（无特殊 uuid）。
     * 若存在多个 manifest 明文块（异常），取首个可解析者。</p>
     */
    public Manifest read() {
        Block b = findBlock();
        if (b == null) {
            return null;
        }
        try {
            return Manifest.fromJson(payloadOf(b.unmasked()));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 扫描全部明文块定位 manifest 引导块（按负载 {@code type=="manifest"} 识别，无特殊 uuid）；
     * 不存在返回 null。
     */
    public Block findBlock() {
        for (Block b : store.scan()) {
            if (!b.isPlaintext()) {
                continue;
            }
            try {
                byte[] payload = payloadOf(b.unmasked());
                com.flora.root.codec.json.model.JsonObject n =
                        com.flora.root.codec.JsonUtil.parseObject(
                                new String(payload, java.nio.charset.StandardCharsets.UTF_8));
                if (StoredNodeType.MANIFEST == StoredNodeType.fromTag(n.getString("type"))) {
                    return b;
                }
            } catch (Exception ignore) {
                // 非 manifest 明文块或损坏块，跳过
            }
        }
        return null;
    }

    /**
     * 写 manifest 明文块（构造 JSON + 计算 MAC + 落盘）。
     * <p>uuid 策略：若已存在 manifest 块则复用其 uuid（覆盖更新），否则生成新的随机 uuid。
     * 负载内不含时间戳；{@code timestamp} 仅作块级前缀参与 MAC/AAD 与冲突仲裁。</p>
     */
    public void write(Manifest m, byte[] macKey, String timestamp) {
        Block existing = findBlock();
        UUID uuid = existing == null ? UUID.randomUUID() : existing.uuid();
        JsonObject manifest = new JsonObject();
        manifest.put("version", m.version());
        manifest.put("type", StoredNodeType.MANIFEST.tag());
        manifest.put("crypto", m.crypto());
        manifest.put("kdf", m.kdf());
        manifest.put("salt", Base64.getEncoder().encodeToString(m.salt()));
        JsonObject params = new JsonObject();
        params.put("memoryKiB", m.memoryKiB());
        params.put("iterations", m.iterations());
        params.put("parallelism", m.parallelism());
        manifest.put("params", params);
        byte[] payload = JsonUtil.toJsonString(manifest).getBytes(StandardCharsets.UTF_8);
        byte[] obf = buildBlock(CipherCodec.uuidBytes(uuid), payload, timestamp, macKey);
        store.put(uuid, obf, null, timestamp);
    }
}
