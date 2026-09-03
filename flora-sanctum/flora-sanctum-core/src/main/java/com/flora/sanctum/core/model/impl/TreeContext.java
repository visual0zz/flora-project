package com.flora.sanctum.core.model.impl;
import com.flora.sanctum.core.model.*;
import com.flora.sanctum.core.model.vault.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.core.crypto.KeyDerivation;
import com.flora.sanctum.core.crypto.KeyIdDeriver;
import com.flora.sanctum.core.crypto.impl.CipherCodec;
import com.flora.sanctum.core.crypto.impl.Envelope;
import com.flora.sanctum.core.crypto.impl.SecureRandomSource;
import com.flora.sanctum.core.store.Block;
import com.flora.sanctum.core.store.ObjectStore;
import com.flora.sanctum.core.store.impl.CipherCodecAdapter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 树操作上下文：数据树节点执行新建/编辑/删除所需的底层能力（存储、加密、DEK 路由、时间戳）。
 * <p>
 * 持有解锁后的 {@link Vault}、内存对象图（unlock 时扫描构建），节点写操作经此落盘并同步内存图。
 */
public final class TreeContext {

    private final ObjectStore store;
    private final Vault vault;
    private final Map<UUID, JsonObject> objects = new LinkedHashMap<>();
    private final Map<UUID, Block> blocks = new LinkedHashMap<>();
    /**
     * 双索引（与 objects/blocks 同步维护）：uuid → 父 uuid（顶层/根概念为 null），
     * 以及父 uuid → 直接子 uuid 列表，使 childrenOf 查表为 O(1)。
     */
    private final Map<UUID, UUID> parentOf = new LinkedHashMap<>();
    private final Map<UUID, List<UUID>> childrenByParent = new LinkedHashMap<>();
    /**
     * 全库块时间戳上限的缓存（解锁时由 scanAll 全量扫描初始化，之后仅增）。
     * 取代 nextTimestamp 每次写都 store.scan() 全库扫描，把导入复杂度从 O(B²) 降到 O(B)。
     * 所有读写均在该 ReentrantLock 内，故无可见性问题。
     */
    private long cachedMaxTs = 1;
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
            byte[] plain = vault.resolve(b.masked(), b.uuid(), b.timestampText());
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
        // 为缺 orderBits 的旧库/导入节点按当前（扫描）顺序赋序：保证展示顺序稳定，
        // 且可被小数索引接管。仅改内存对象图（objects），不强制落盘，首次被编辑时随块写入。
        for (List<UUID> sibs : childrenByParent.values()) {
            double o = 0.0;
            for (UUID u : sibs) {
                JsonObject obj = objects.get(u);
                if (obj != null && obj.getLong("orderBits") == null) {
                    obj.put("orderBits", Double.doubleToLongBits(o));
                }
                o += FractionalIndex.D;
            }
        }
        // 初始化时间戳上限缓存：覆盖全部块（含 manifest/root/数据块），与解锁时 baseTimestamp 同源。
        long max = 1;
        for (Block b : blocks.values()) {
            if (b.timestamp() > max) {
                max = b.timestamp();
            }
        }
        cachedMaxTs = max;
    }

    /**
     * 维护 parentOf / childrenByParent 索引（从对象 parent 字段解析）。
     * <p>幂等：写入前先清除该 uuid 既有的索引位置。setIcon / rename 会以同一 uuid 二次写入对象，
     * 若不清除则会在父组的子列表里重复追加（表现为组/条目在树中重复渲染）；parent 变化的移动场景
     * 也借此从旧父子列表移除。仅当目标父下确实不含该 uuid 时才追加，杜绝同父重复项。</p>
     */
    private void indexObject(UUID uuid, JsonObject obj) {
        UUID parent = resolveParent(obj.getString("parent"));
        UUID oldParent = parentOf.get(uuid);
        if (oldParent != null) {
            List<UUID> oldSiblings = childrenByParent.get(oldParent);
            if (oldSiblings != null) {
                oldSiblings.remove(uuid);
            }
        }
        parentOf.put(uuid, parent);
        if (parent != null) {
            List<UUID> siblings = childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>());
            if (!siblings.contains(uuid)) {
                siblings.add(uuid);
            }
        }
    }

    /** parent 字段 → 父 uuid（可解析 UUID 则返回，否则 null）。 */
    private UUID resolveParent(String parent) {
        if (parent == null || !isUuid(parent)) {
            return null;
        }
        return com.flora.sanctum.core.util.UuidHex.fromHex(parent);
    }

    private static boolean isUuid(String s) {
        try {
            com.flora.sanctum.core.util.UuidHex.fromHex(s);
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
        // 子节点新建/编辑/标记删除后，尝试对其归属组做惰性密钥轮换（前向保密）
        UUID parentGroup = (groupId == null) ? vault.rootObjectUuid() : groupId;
        maybeRotateGroupKeys(parentGroup);
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
            // 时间戳取齐 + 落盘在锁内原子完成：避免并发写读到相同 max 时间戳产生碰撞或交错落盘。
            long ts = nextTimestamp();
            String tsText = Long.toString(ts);
            Block written = store.put(uuid, json, new CipherCodecAdapter(codec, uuid), tsText);
            objects.put(uuid, payload);
            blocks.put(uuid, written);
            indexObject(uuid, payload);
            // 维护时间戳上限缓存（仅增），避免每次写都全库 scan。
            if (ts > cachedMaxTs) {
                cachedMaxTs = ts;
            }
        } finally {
            lock.unlock();
        }
    }

    /** 删除对象并同步内存图、索引与块定位。 */
    public void delete(UUID uuid) {
        lock.lock();
        UUID parent = null;
        try {
            parent = parentOf.get(uuid);
            store.delete(uuid);
            objects.remove(uuid);
            blocks.remove(uuid);
            if (parent != null) {
                List<UUID> siblings = childrenByParent.get(parent);
                if (siblings != null) {
                    siblings.remove(uuid);
                }
            }
            parentOf.remove(uuid);
        } finally {
            lock.unlock();
        }
        // 硬删除后其父组的退役 dek1 使用数可能下降 → 尝试轮换（含软删除块计入）
        if (parent != null) {
            maybeRotateGroupKeys(parent);
        }
    }

    /** 找加密归属 DEK：顶层（groupId=null 或 root uuid）用 rootDek，否则用对应 group DEK，兜底 KEK。 */
    public byte[] dekFor(UUID groupId) {
        if (groupId == null) {
            // 顶层对象归属根对象：rootDek（注册为 groupDek(rootObjectUuid)）；未登记时兜底 KEK
            byte[] root = vault.rootDek();
            return root != null ? root : vault.dataDek();
        }
        if (vault.groupDek(groupId) != null) {
            return vault.groupDek(groupId);
        }
        return vault.dataDek();
    }

    /**
     * 计算本次写入的时间戳（仓库时间戳规则：max(会话锚点+单调偏移, 全库当前最大块时间戳)）。
     * <p>全库最大块时间戳取自缓存 {@link #cachedMaxTs}（写时仅增维护），不再每次 store.scan() 全库。
     * 该缓存在 scanAll 全量初始化、writeCipherBlock/delete 路径维护，并经 {@link #noteTimestamp} 覆盖
     * 绕过 writeCipherBlock 的直接落盘（如 manifest 改写），故此处读到的上限与全扫一致。</p>
     */
    public long nextTimestamp() {
        long maxExisting;
        lock.lock();
        try {
            maxExisting = cachedMaxTs;
        } finally {
            lock.unlock();
        }
        return vault.clock().timestampCappedAt(maxExisting);
    }

    /**
     * 回写时间戳上限：供直接经 store.put 落盘（绕过 writeCipherBlock，如主密码轮换改写 manifest）
     * 的写入使用，使其时间戳被缓存感知，保持 {@link #nextTimestamp} 权威。
     */
    void noteTimestamp(long ts) {
        lock.lock();
        try {
            if (ts > cachedMaxTs) {
                cachedMaxTs = ts;
            }
        } finally {
            lock.unlock();
        }
    }

    /** 按 parent 列出直接子对象 uuid（O(1) 索引查表，按 order 升序返回快照副本）。 */
    public List<UUID> childrenOf(UUID parent) {
        lock.lock();
        try {
            List<UUID> siblings = childrenByParent.get(parent);
            if (siblings == null) {
                return List.of();
            }
            List<UUID> sorted = new ArrayList<>(siblings);
            sorted.sort((a, b) -> Double.compare(orderOf(a), orderOf(b)));
            return List.copyOf(sorted);
        } finally {
            lock.unlock();
        }
    }

    /** 节点的排序键 order（来自块内 orderBits；缺失按 0）。 */
    public double orderOf(UUID uuid) {
        lock.lock();
        try {
            JsonObject o = objects.get(uuid);
            Long bits = o == null ? null : o.getLong("orderBits");
            return bits == null ? 0.0 : Double.longBitsToDouble(bits);
        } finally {
            lock.unlock();
        }
    }

    /** parent 下当前最大 order（无子返回 0），供新建/追加时取 max + D。 */
    public double maxOrderUnder(UUID parent) {
        lock.lock();
        try {
            double max = 0.0;
            for (UUID c : childrenOf(parent)) {
                max = Math.max(max, orderOf(c));
            }
            return max;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 组密钥惰性轮换（前向保密，见设计"组密钥轮换"）。
     * <p>
     * 新/改子节点一律用活跃 dek2（{@link #dekFor} 取 dek2）。若退役中的 dek1 已无任何直接子节点
     * 或条目字段使用（含软删除块——其仅打标记、保留 parent 链路，故仍在 {@code childrenOf} 中、
     * 计入使用），则丢弃 dek1、把 dek2 提升为 dek1 并随机新建 dek2，然后用新密钥对重写组块。
     * dek 与组 JSON 其他字段整体被父 DEK 加密，不单独包裹（避免双层加密）。
     * <p>
     * 仅枚举本组直接子节点与其条目字段判定 dek1 是否仍被使用——它们才是用本组 DEK 加密的对象，
     * 不扫描全库；孙子节点用其自身父组 DEK，不在此范围。
     */
    public void maybeRotateGroupKeys(UUID groupUuid) {
        if (groupUuid == null) {
            return;
        }
        Vault.GroupKeys keys = vault.groupKeys(groupUuid);
        if (keys == null) {
            return;
        }
        byte[] dek1 = keys.dek1();
        byte[] dek2 = keys.dek2();
        // 尚未开始迁移（含旧格式单 dek：dek1==dek2）时不轮换
        if (Arrays.equals(dek1, dek2)) {
            return;
        }
        if (dek1StillUsed(groupUuid, dek1)) {
            return; // dek1 仍被使用，不丢弃
        }
        // 轮换：dek2 提升为 dek1，随机新建 dek2
        byte[] newDek2 = new byte[32];
        random().nextBytes(newDek2);
        vault.replaceGroupDek(groupUuid, dek2, newDek2);
        rewriteGroupKeys(groupUuid, dek2, newDek2);
    }

    /** 退役中 dek1 是否仍被本组任一直接子节点/条目字段使用（含软删除块）。 */
    private boolean dek1StillUsed(UUID groupUuid, byte[] dek1) {
        byte[] seed = vault.repoKeyIdSeed();
        if (seed == null) {
            return true; // 无法判定时保守：认为仍在使用，不丢弃
        }
        for (UUID child : childrenOf(groupUuid)) {
            if (blockUsesDek(child, dek1, seed)) {
                return true;
            }
        }
        // 条目字段的 parent 指向条目而非本组，但其块用本组 DEK 加密，需单列枚举
        for (UUID entry : childrenOf(groupUuid)) {
            JsonObject e = read(entry);
            if (e == null || StoredNodeType.ENTRY != StoredNodeType.fromTag(e.getString("type"))) {
                continue;
            }
            for (UUID field : childrenOf(entry)) {
                if (blockUsesDek(field, dek1, seed)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 块是否用指定 DEK 加密：仅读信封头 (nonce, keyId) 反推 dekId 比较，不解密、不扫描全库。 */
    private boolean blockUsesDek(UUID uuid, byte[] dek, byte[] seed) {
        Block b = blockOf(uuid);
        if (b == null || !b.isCipher()) {
            return false;
        }
        byte[] raw = b.masked();
        if (raw.length < Envelope.HEADER_LEN) {
            return false;
        }
        int no = Envelope.MAGIC_LEN + 2;
        byte[] nonce = Arrays.copyOfRange(raw, no, no + Envelope.NONCE_LEN);
        byte[] keyId = Arrays.copyOfRange(raw, no + Envelope.NONCE_LEN,
                no + Envelope.NONCE_LEN + Envelope.KEYID_LEN);
        byte[] cid = KeyIdDeriver.resolveDekId(seed, nonce, keyId);
        return Arrays.equals(cid, KeyIdDeriver.dekId(dek));
    }

    /** 用新密钥对重写组（含根）块；dek1/dek2 随组 JSON 整体被父 DEK 加密，移除旧单 dek 字段。 */
    private void rewriteGroupKeys(UUID groupUuid, byte[] dek1, byte[] dek2) {
        JsonObject json = read(groupUuid);
        if (json == null) {
            return;
        }
        json.put("dek1", java.util.Base64.getEncoder().encodeToString(dek1));
        json.put("dek2", java.util.Base64.getEncoder().encodeToString(dek2));
        json.remove("dek");
        if (groupUuid.equals(vault.rootObjectUuid())) {
            // 根对象块整体以 KEK 加密（外层保护）
            writeWithDek(groupUuid, json, vault.dataDek());
        } else {
            write(groupUuid, json, parentUuidOf(groupUuid));
        }
    }
}
