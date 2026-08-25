package com.flora.sanctum.model.impl;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.crypto.impl.Envelope;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
import com.flora.sanctum.store.Block;
import com.flora.sanctum.store.ObjectStore;

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
 * MAC = HMAC-SHA256(macKey, 完整信封头 ‖ 时间戳 ‖ 负载)，不存于 JSON 内部。
 * 读：扫描找 type=manifest 明文块 → 拆出负载解析为 {@link Manifest}；
 * 写：构造负载 JSON + 计算 MAC + 拼块落盘。
 */
public final class ManifestStore {

    private final ObjectStore store;
    private final SecureRandomSource random;

    public ManifestStore(ObjectStore store, SecureRandomSource random) {
        this.store = store;
        this.random = random;
    }

    // ---- 块构造 / 解析（静态工具，供 VaultCreator / VaultUnlocker 复用） ----

    /** 构造明文块信封头：magic+version+flags+uuid（PLAINTEXT_HEADER_LEN 字节）。 */
    public static byte[] plaintextHeader(UUID uuid) {
        byte[] header = new byte[Envelope.PLAINTEXT_HEADER_LEN];
        System.arraycopy(Envelope.MAGIC, 0, header, 0, Envelope.MAGIC_LEN);
        header[Envelope.MAGIC_LEN] = Envelope.VERSION_1;
        header[Envelope.MAGIC_LEN + 1] = Envelope.FLAG_PLAINTEXT;
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(header, Envelope.MAGIC_LEN + 2, 16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return header;
    }

    /** MAC 输入：时间戳(ASCII 原文) ‖ 完整信封头 ‖ 负载。 */
    public static byte[] macInput(byte[] header, String timestamp, byte[] payload) {
        byte[] ts = timestamp.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[ts.length + header.length + payload.length];
        System.arraycopy(ts, 0, out, 0, ts.length);
        System.arraycopy(header, 0, out, ts.length, header.length);
        System.arraycopy(payload, 0, out, ts.length + header.length, payload.length);
        return out;
    }

    /** 计算 manifest MAC：HMAC-SHA256(macKey, timestamp(ASCII 原文) ‖ header ‖ payload)。 */
    public static byte[] computeMac(byte[] macKey, byte[] header, String timestamp, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(macKey, "HmacSHA256"));
            return mac.doFinal(macInput(header, timestamp, payload));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 构造完整明文块（含异或混淆）：header + payload + mac。 */
    public static byte[] buildBlock(UUID uuid, byte[] payload, String timestamp, byte[] macKey) {
        byte[] header = plaintextHeader(uuid);
        byte[] mac = computeMac(macKey, header, timestamp, payload);
        byte[] block = new byte[header.length + payload.length + mac.length];
        System.arraycopy(header, 0, block, 0, header.length);
        System.arraycopy(payload, 0, block, header.length, payload.length);
        System.arraycopy(mac, 0, block, header.length + payload.length, mac.length);
        SecureRandomSource rng = new SecureRandomSource();
        byte xor = rng.nextByte();
        byte[] out = new byte[block.length];
        for (int i = 0; i < block.length; i++) {
            out[i] = (byte) (block[i] ^ xor);
        }
        return out;
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

    /** 校验完整明文块 MAC（header+timestamp(ASCII 原文)+payload 与尾附 MAC 比对）。 */
    public static boolean verifyMac(byte[] full, String timestamp, byte[] macKey) {
        byte[] expected = computeMac(macKey, headerOf(full), timestamp, payloadOf(full));
        return MessageDigestIsEqual(expected, macOf(full));
    }

    private static boolean MessageDigestIsEqual(byte[] a, byte[] b) {
        return java.security.MessageDigest.isEqual(a, b);
    }

    // ---- 实例读写 ----

    /** 查找 manifest 块 uuid（找不到抛 IllegalStateException）。 */
    public UUID findUuid() {
        Block b = findBlock();
        if (b == null) {
            throw new IllegalStateException("manifest not found");
        }
        return b.uuid();
    }

    /** 读取 manifest；不存在返回 null。 */
    public Manifest read() {
        Block b = findBlock();
        if (b == null) {
            return null;
        }
        try {
            return Manifest.fromJson(payloadOf(b.deobfuscated()));
        } catch (Exception e) {
            return null;
        }
    }

    /** 查找 manifest 明文块（含物理定位）；不存在返回 null。 */
    public Block findBlock() {
        for (Block b : store.scan()) {
            if (b.isPlaintext()) {
                byte[] full = b.deobfuscated();
                if (full.length <= Envelope.PLAINTEXT_HEADER_LEN + Envelope.MANIFEST_MAC_LEN) {
                    continue;
                }
                try {
                    JsonObject n = JsonUtil.parseObject(new String(payloadOf(full), StandardCharsets.UTF_8));
                    if (NodeType.MANIFEST == NodeType.fromTag(n.getString("type"))) {
                        return b;
                    }
                } catch (Exception ignore) {
                }
            }
        }
        return null;
    }

    /** 写 manifest 明文块（构造 JSON + 计算 MAC + 落盘）。 */
    public void write(Manifest m, byte[] macKey) {
        UUID uuid = findUuid();
        JsonObject manifest = new JsonObject();
        manifest.put("version", m.version());
        manifest.put("type", NodeType.MANIFEST.tag());
        manifest.put("cryptoVersion", m.cryptoVersion());
        manifest.put("kdf", m.kdf());
        manifest.put("salt", Base64.getEncoder().encodeToString(m.salt()));
        JsonObject params = new JsonObject();
        params.put("m", m.memoryKiB());
        params.put("i", m.iterations());
        params.put("p", m.parallelism());
        manifest.put("params", params);
        manifest.put("rootGroupUuid", m.rootGroupUuid() == null ? null : m.rootGroupUuid().toString());
        manifest.put("updateTimestamp", m.updateTimestamp());
        byte[] payload = JsonUtil.toJsonString(manifest).getBytes(StandardCharsets.UTF_8);
        byte[] obf = buildBlock(uuid, payload, Long.toString(m.updateTimestamp()), macKey);
        store.put(uuid, obf, null, Long.toString(m.updateTimestamp()));
    }
}
