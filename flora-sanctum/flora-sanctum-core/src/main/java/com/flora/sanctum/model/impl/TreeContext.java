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
import java.util.concurrent.locks.ReentrantLock;

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
    /**
     * 双索引（与 objects/blocks 同步维护）：uuid → 父 uuid（顶层/根概念为 null），
     * 以及父 uuid → 直接子 uuid 列表。取代 childrenOf 的全图线性扫描（O(n)→O(1)）。
     */
    private final Map<UUID, UUID> parentOf = new LinkedHashMap<>();
    private final Map<UUID, List<UUID>> childrenByParent = new LinkedHashMap<>();
    /** 守护内存图、两张索引与底层 store 的一致性：write/delete 整段（含时间戳 scan+落盘）原子。 */
    private final ReentrantLock lock = new ReentrantLock();

    public TreeContext(ObjectStore store, Vault vault) {
        this.store = store;
        this.vault = vault;
        scanAll();
    }

    private void scanAll() {
        for (Block b : store.scan()) {
            // 缓存全部块（含当前不可解密的），供 blockOf 定位；解密成功的才进对象图。
            blocks.put(b.uuid(), b);
            byte[] plain = vault.resolve(b.masked(), b.timestampText());
            if (plain == null) {
                continue;
            }
            try {
                JsonObject obj = JsonUtil.parseObject(new String(plain, StandardCharsets.UTF_8));
                objects.put(b.uuid(), obj);
                indexObject(b.uuid(), obj);
            } catch (Exception ignore) {
                // 无法解析的块跳过
            }
        }
    }

    /** 维护 parentOf / childrenByParent 索引（从对象 parent 字段解析）。 */
    private void indexObject(UUID uuid, JsonObject obj) {
        UUID parent = resolveParent(obj.getString("parent"));
        parentOf.put(uuid, parent);
        if (parent != null) {
            childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(uuid);
        }
    }

    /** parent 字段 → 父 uuid（可解析 UUID 则返回，否则 null）。 */
    private UUID resolveParent(String parent) {
        if (parent == null || !isUuid(parent)) {
            return null;
        }
        return UUID.fromString(parent);
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 某对象的原始块定位（文件+行号，供审计/去重/恢复）。已缓存直接返回；新写入对象惰性定位一次。 */
    public Block blockOf(UUID uuid) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
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

    /** 全部对象（内存图快照，供树构建/遍历/搜索）。 */
    public Map<UUID, JsonObject> objects() {
        lock.lock();
        try {
            return new LinkedHashMap<>(objects);
        } finally {
            lock.unlock();
        }
    }

    /** 读取对象负载；未找到返回 null。 */
    public JsonObject read(UUID uuid) {
        lock.lock();
        try {
            return objects.get(uuid);
        } finally {
            lock.unlock();
        }
    }

    /** 所属组 uuid（解析 parent；根概念 tag / 非 uuid 返回 null）。 */
    public UUID parentGroupUuid(JsonObject obj) {
        return resolveParent(obj == null ? null : obj.getString("parent"));
    }

    /** 对象的父 uuid（来自索引；顶层/根概念返回 null）。 */
    public UUID parentUuidOf(UUID uuid) {
        lock.lock();
        try {
            return parentOf.get(uuid);
        } finally {
            lock.unlock();
        }
    }

    /** 按归属加密写入（子文件夹用文件夹 DEK，顶层用 data 根 DEK），并同步内存图与索引。 */
    public void write(UUID uuid, JsonObject payload, UUID groupId) {
        writeCipherBlock(uuid, payload, dekFor(groupId));
    }

    /** 用指定 DEK 加密写入（icon/sshKey/remote 按根概念路由），并同步内存图与索引。 */
    public void writeWithDek(UUID uuid, JsonObject payload, byte[] dek) {
        writeCipherBlock(uuid, payload, dek);
    }

    private void writeCipherBlock(UUID uuid, JsonObject payload, byte[] dek) {
        lock.lock();
        try {
            byte[] json = JsonUtil.toJsonString(payload).getBytes(StandardCharsets.UTF_8);
            byte[] encKey = KeyDerivation.encKey(dek);
            CipherCodec codec = new CipherCodec(encKey, dek, vault.repoKeyIdSeed(), vault.random());
            // 时间戳 scan + 落盘在锁内原子完成：避免并发写读到相同 max 时间戳产生碰撞或交错落盘。
            long ts = nextTimestamp();
            String tsText = Long.toString(ts);
            Block written = store.put(uuid, json, new CipherCodecAdapter(codec, uuid), tsText);
            objects.put(uuid, payload);
            blocks.put(uuid, written);
            indexObject(uuid, payload);
        } finally {
            lock.unlock();
        }
    }

    /** 删除对象并同步内存图、索引与块定位。 */
    public void delete(UUID uuid) {
        lock.lock();
        try {
            store.delete(uuid);
            objects.remove(uuid);
            blocks.remove(uuid);
            UUID parent = parentOf.remove(uuid);
            if (parent != null) {
                List<UUID> siblings = childrenByParent.get(parent);
                if (siblings != null) {
                    siblings.remove(uuid);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /** 找加密归属 DEK：条目/字段若在子 group 下用该 group DEK，否则用 data 根。 */
    public byte[] dekFor(UUID groupId) {
        if (groupId != null && vault.groupDek(groupId) != null) {
            return vault.groupDek(groupId);
        }
        return vault.dataDek();
    }

    /** 用父 DEK 包裹一个 DEK（AES-GCM-SIV，nonce 随机；内部信封无块时间戳，timestamp=0）。 */
    public byte[] wrapDek(byte[] dek, byte[] parentDek) {
        byte[] encKey = KeyDerivation.encKey(parentDek);
        CipherCodec codec = new CipherCodec(encKey, parentDek, vault.repoKeyIdSeed(), vault.random());
        return codec.encode(UUID.randomUUID(), dek, "0");
    }

    /** 计算本次写入的时间戳（仓库时间戳规则：max(会话锚点+单调偏移, 全库当前最大块时间戳)）。 */
    public long nextTimestamp() {
        long maxExisting = 1;
        for (Block b : store.scan()) {
            if (b.timestamp() > maxExisting) {
                maxExisting = b.timestamp();
            }
        }
        return vault.clock().timestampCappedAt(maxExisting);
    }

    /** 按 parent 列出直接子对象 uuid（O(1) 索引查表，返回快照副本）。 */
    public List<UUID> childrenOf(UUID parent) {
        lock.lock();
        try {
            List<UUID> siblings = childrenByParent.get(parent);
            return siblings == null ? List.of() : List.copyOf(siblings);
        } finally {
            lock.unlock();
        }
    }
}
