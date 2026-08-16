package com.flora.sanctum.model.impl;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.crypto.KeyDerivation;
import com.flora.sanctum.crypto.impl.CipherCodec;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
import com.flora.sanctum.store.Block;
import com.flora.sanctum.store.ObjectStore;
import com.flora.sanctum.store.impl.CipherCodecAdapter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 树操作上下文：数据树节点执行新建/编辑/删除所需的底层能力（存储、加密、DEK 路由、时间戳）。
 * <p>
 * 持有解锁后的 {@link Vault}、内存对象图（unlock 时扫描构建），节点写操作经此落盘并同步内存图。
 * 对应重构前 {@code Sanctum} 的私有方法（writeObject/readObject/resolveDekFor/nextTimestamp/wrap）。
 */
public final class TreeContext {

    private final ObjectStore store;
    private final Vault vault;
    private final Map<UUID, JsonObject> objects = new LinkedHashMap<>();
    private final Map<UUID, Block> blocks = new LinkedHashMap<>();

    public TreeContext(ObjectStore store, Vault vault) {
        this.store = store;
        this.vault = vault;
        scanAll();
    }

    private void scanAll() {
        for (Block b : store.scan()) {
            byte[] plain = vault.resolve(b.obfuscated());
            if (plain == null) {
                continue;
            }
            try {
                objects.put(b.uuid(), JsonUtil.parseObject(new String(plain, StandardCharsets.UTF_8)));
                blocks.put(b.uuid(), b);
            } catch (Exception ignore) {
                // 无法解析的块跳过
            }
        }
    }

    /** 某对象的原始块定位（文件+行号，供审计/去重/恢复）。已缓存直接返回；新写入对象惰性定位一次。 */
    public Block blockOf(UUID uuid) {
        Block cached = blocks.get(uuid);
        if (cached != null) {
            return cached;
        }
        for (Block b : store.scan()) {
            if (b.uuid().equals(uuid)) {
                blocks.put(uuid, b);
                return b;
            }
        }
        return null;
    }

    public Vault vault() {
        return vault;
    }

    public ObjectStore store() {
        return store;
    }

    public SecureRandomSource random() {
        return vault.random();
    }

    /** 全部对象（内存图，供树构建/遍历/搜索）。 */
    public Map<UUID, JsonObject> objects() {
        return objects;
    }

    /** 读取对象负载；未找到返回 null。 */
    public JsonObject read(UUID uuid) {
        return objects.get(uuid);
    }

    /** 所属组 uuid（解析 parent；根概念 tag / 非 uuid 返回 null）。 */
    public UUID parentGroupUuid(JsonObject obj) {
        String p = obj == null ? null : obj.getString("parent");
        if (p == null || !isUuid(p)) {
            return null;
        }
        return UUID.fromString(p);
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 按归属加密写入（子文件夹用文件夹 DEK，顶层用 data 根 DEK），并同步内存图。 */
    public void write(UUID uuid, JsonObject payload, UUID groupId) {
        writeCipherBlock(uuid, payload, dekFor(groupId));
    }

    /** 用指定 DEK 加密写入（icon/sshKey/remote 按根概念路由），并同步内存图。 */
    public void writeWithDek(UUID uuid, JsonObject payload, byte[] dek) {
        writeCipherBlock(uuid, payload, dek);
    }

    private void writeCipherBlock(UUID uuid, JsonObject payload, byte[] dek) {
        byte[] json = JsonUtil.toJsonString(payload).getBytes(StandardCharsets.UTF_8);
        byte[] encKey = KeyDerivation.encKey(dek);
        CipherCodec codec = new CipherCodec(encKey, dek, vault.random());
        store.put(uuid, json, new CipherCodecAdapter(codec, uuid));
        objects.put(uuid, payload);
    }

    /** 删除对象并同步内存图与块定位。 */
    public void delete(UUID uuid) {
        store.delete(uuid);
        objects.remove(uuid);
        blocks.remove(uuid);
    }

    /** 找加密归属 DEK：条目/字段若在子文件夹下用该文件夹 DEK，否则用 data 根。 */
    public byte[] dekFor(UUID groupId) {
        if (groupId != null && vault.folderDek(groupId) != null) {
            return vault.folderDek(groupId);
        }
        return vault.dekForRole(RootTag.DATA);
    }

    /** 用父 DEK 包裹一个 DEK（AES-GCM-SIV，nonce 随机）。 */
    public byte[] wrapDek(byte[] dek, byte[] parentDek) {
        byte[] encKey = KeyDerivation.encKey(parentDek);
        CipherCodec codec = new CipherCodec(encKey, parentDek, vault.random());
        return codec.encode(UUID.randomUUID(), dek, codec.makeKeyIdWith(parentDek));
    }

    /** 计算本次写入的 updateTimestamp（仓库时间戳规则：max(会话偏移+锚点, 全库最大)）。 */
    public long nextTimestamp() {
        long maxExisting = 1;
        for (JsonObject n : objects.values()) {
            Long t = n.getLong("updateTimestamp");
            if (t != null && t > maxExisting) {
                maxExisting = t;
            }
        }
        return vault.clock().nextTimestamp(maxExisting);
    }

    /** 按 parent 列出直接子对象 uuid（内存图遍历）。 */
    public List<UUID> childrenOf(UUID parent) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, JsonObject> e : objects.entrySet()) {
            String p = e.getValue().getString("parent");
            if (p != null && p.equals(parent.toString())) {
                out.add(e.getKey());
            }
        }
        return out;
    }
}
