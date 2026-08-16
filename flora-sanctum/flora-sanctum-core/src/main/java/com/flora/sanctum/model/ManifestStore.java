package com.flora.sanctum.model;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.crypto.impl.Envelope;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
import com.flora.sanctum.store.Block;
import com.flora.sanctum.store.BlockHeader;
import com.flora.sanctum.store.ObjectStore;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * manifest 明文引导块读写（收敛 VaultCreator / Sanctum.close / MasterKeyRotator 三处手拼逻辑）。
 * <p>
 * 读：扫描找 type=manifest 明文块 → 解析为 {@link Manifest}；
 * 写：构造含 MAC 的明文块（MAC 覆盖信封头 uuid + 负载全部字段）并落盘。
 */
public final class ManifestStore {

    private final ObjectStore store;
    private final SecureRandomSource random;

    public ManifestStore(ObjectStore store, SecureRandomSource random) {
        this.store = store;
        this.random = random;
    }

    /** 查找 manifest 块 uuid（找不到抛 IllegalStateException）。 */
    public UUID findUuid() {
        for (Block b : store.scan()) {
            if (b.isPlaintext()) {
                byte[] full = b.deobfuscated();
                byte[] payload = payloadOf(full);
                try {
                    JsonObject n = JsonUtil.parseObject(new String(payload, StandardCharsets.UTF_8));
                    if ("manifest".equals(n.getString("type"))) {
                        return b.uuid();
                    }
                } catch (Exception ignore) {
                }
            }
        }
        throw new IllegalStateException("manifest not found");
    }

    /** 读取 manifest；不存在返回 null。 */
    public Manifest read() {
        for (Block b : store.scan()) {
            if (b.isPlaintext()) {
                byte[] full = b.deobfuscated();
                byte[] payload = payloadOf(full);
                try {
                    return Manifest.fromJson(payload);
                } catch (Exception ignore) {
                    // 非 manifest 明文块，跳过
                }
            }
        }
        return null;
    }

    /** 写 manifest 明文块（构造 JSON + 计算 MAC + 落盘）。 */
    public void write(Manifest m, byte[] macKey) {
        UUID uuid = findUuid();
        byte[] mac = m.computeMac(macKey, uuid);
        JsonObject manifest = new JsonObject();
        manifest.put("version", m.version());
        manifest.put("type", "manifest");
        manifest.put("parent", RootTag.MANIFEST.tag());
        manifest.put("cryptoVersion", m.cryptoVersion());
        manifest.put("kdf", m.kdf());
        manifest.put("salt", Base64.getEncoder().encodeToString(m.salt()));
        JsonObject params = new JsonObject();
        params.put("m", m.memoryKiB());
        params.put("i", m.iterations());
        params.put("p", m.parallelism());
        manifest.put("params", params);
        manifest.put("warehouseTime", m.warehouseTime());
        manifest.put("updateTimestamp", m.updateTimestamp());
        manifest.put("mac", Base64.getEncoder().encodeToString(mac));
        writePlaintextBlock(uuid, manifest);
    }

    private static byte[] payloadOf(byte[] full) {
        byte[] payload = new byte[full.length - Envelope.PLAINTEXT_HEADER_LEN];
        System.arraycopy(full, Envelope.PLAINTEXT_HEADER_LEN, payload, 0, payload.length);
        return payload;
    }

    private void writePlaintextBlock(UUID uuid, JsonObject payload) {
        byte[] json = JsonUtil.toJsonString(payload).getBytes(StandardCharsets.UTF_8);
        byte[] block = new byte[Envelope.PLAINTEXT_HEADER_LEN + json.length];
        System.arraycopy(Envelope.MAGIC, 0, block, 0, Envelope.MAGIC_LEN);
        block[Envelope.MAGIC_LEN] = Envelope.VERSION_1;
        block[Envelope.MAGIC_LEN + 1] = Envelope.FLAG_PLAINTEXT;
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(block, Envelope.MAGIC_LEN + 2, 16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        System.arraycopy(json, 0, block, Envelope.PLAINTEXT_HEADER_LEN, json.length);
        byte xor = random.nextByte();
        byte[] obf = BlockHeader.obfuscate(block, xor);
        store.put(uuid, obf, null);
    }
}
