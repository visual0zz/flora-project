package com.flora.sanctum.model;

import com.flora.sanctum.crypto.Argon2Kdf;
import com.flora.sanctum.store.Block;
import com.flora.sanctum.store.ObjectStore;
import com.flora.sanctum.store.impl.MarkdownObjectStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 密码库门面（对外主入口）。
 * <p>
 * 整合存储、解锁、目录与条目/组/字段 CRUD（见设计 05"密码库适配器"）。
 * 目录 = 解锁后扫描全部对象、按 type 分类、在内存构建引用图；锁定即丢弃。
 */
public final class Sanctum implements AutoCloseable {

    private final Path root;
    private final MarkdownObjectStore store;
    private Vault vault;
    private Directory directory;

    private Sanctum(Path root) {
        this.root = root;
        this.store = new MarkdownObjectStore(root);
    }

    /** 重新扫描并重建内存目录（写入/删除后调用）。 */
    private void refresh() {
        if (vault != null) {
            this.directory = Directory.build(vault);
        }
    }

    /** 打开（不锁定）。 */
    public static Sanctum open(Path root) {
        return new Sanctum(root);
    }

    /** 新建并解锁。 */
    public static Sanctum createAndUnlock(Path root, char[] masterPassword) {
        Sanctum s = new Sanctum(root);
        new VaultCreator(s.store).create(masterPassword);
        s.unlock(masterPassword);
        return s;
    }

    /** 解锁：加载 manifest、KEK、root DEK、构建目录。 */
    public void unlock(char[] masterPassword) {
        this.vault = new VaultUnlocker(store).unlock(masterPassword);
        this.directory = Directory.build(vault);
    }

    public void lock() {
        if (vault != null) {
            vault.clearSecrets();
        }
        this.vault = null;
        this.directory = null;
    }

    /**
     * 关闭库：更新 warehouseTime 并重写 manifest（含重算 MAC），然后锁定。
     */
    public void close() {
        if (vault == null) {
            return;
        }
        vault.clock().close();
        long newWarehouseTime = vault.clock().warehouseTime();
        // 找 manifest 块及其 uuid
        java.util.UUID manifestUuid = findManifestUuid();
        Manifest m = vault.manifest();
        byte[] macKey = m.manifestMacKey(vault.kek());
        // 用更新后的 warehouseTime 构造新 manifest 计算 MAC（负载其它字段沿用）
        Manifest updated = new Manifest(m.version(), m.cryptoVersion(), m.kdf(), m.salt(),
                m.memoryKiB(), m.iterations(), m.parallelism(), newWarehouseTime, m.updateTimestamp(), new byte[0]);
        byte[] mac = updated.computeMac(macKey, manifestUuid);
        Json.Node manifest = Json.obj();
        Json.put(manifest, "version", Json.of(updated.version()));
        Json.put(manifest, "type", Json.of("manifest"));
        Json.put(manifest, "cryptoVersion", Json.of(updated.cryptoVersion()));
        Json.put(manifest, "kdf", Json.of(updated.kdf()));
        Json.put(manifest, "salt", Json.of(java.util.Base64.getEncoder().encodeToString(updated.salt())));
        Json.Node params = Json.obj();
        Json.put(params, "m", Json.of(updated.memoryKiB()));
        Json.put(params, "i", Json.of(updated.iterations()));
        Json.put(params, "p", Json.of(updated.parallelism()));
        Json.put(manifest, "params", params);
        Json.put(manifest, "warehouseTime", Json.of(newWarehouseTime));
        Json.put(manifest, "updateTimestamp", Json.of(updated.updateTimestamp()));
        Json.put(manifest, "mac", Json.of(java.util.Base64.getEncoder().encodeToString(mac)));
        writeManifestPlaintextBlock(manifestUuid, manifest);
        lock();
    }

    private java.util.UUID findManifestUuid() {
        for (com.flora.sanctum.store.Block b : store.scan()) {
            if (b.isPlaintext()) {
                byte[] full = b.deobfuscated();
                byte[] payload = new byte[full.length - 22];
                System.arraycopy(full, 22, payload, 0, payload.length);
                try {
                    Json.Node n = Json.parse(new String(payload, java.nio.charset.StandardCharsets.UTF_8));
                    if ("manifest".equals(n.str("type"))) {
                        return b.uuid();
                    }
                } catch (Exception ignore) {
                }
            }
        }
        throw new IllegalStateException("manifest not found");
    }

    private void writeManifestPlaintextBlock(java.util.UUID uuid, Json.Node payload) {
        byte[] json = Json.stringify(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] block = new byte[6 + 16 + json.length];
        System.arraycopy(com.flora.sanctum.crypto.Envelope.MAGIC, 0, block, 0, 4);
        block[4] = com.flora.sanctum.crypto.Envelope.VERSION_1;
        block[5] = com.flora.sanctum.crypto.Envelope.FLAG_PLAINTEXT;
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(block, 6, 16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        System.arraycopy(json, 0, block, 22, json.length);
        byte xor = vault.random().nextByte();
        byte[] obf = com.flora.sanctum.store.BlockHeader.obfuscate(block, xor);
        try {
            java.nio.file.Files.writeString(root.resolve(uuid + ".md"),
                    com.flora.root.codec.Base58.encode(obf) + "\n",
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("rewrite manifest failed", e);
        }
    }

    public boolean isUnlocked() {
        return vault != null;
    }

    public Vault vault() {
        return vault;
    }

    /** 取某文件夹的 DEK（null 若未发现）。 */
    public byte[] folderDek(java.util.UUID groupUuid) {
        return vault == null ? null : vault.folderDek(groupUuid);
    }

    public Directory directory() {
        return directory;
    }

    public ObjectStore store() {
        return store;
    }

    public Path root() {
        return root;
    }

    // ---- 条目 CRUD ----

    /**
     * 新建一个文件夹（group）。每个文件夹绑定一个 DEK，子文件夹 DEK 用父 DEK 包裹
     * （顶层子文件夹用 objects root DEK 包裹），见设计 02"文件夹 DEK"。
     *
     * @param parentId 父文件夹 UUID（null=普通对象 root）
     * @param name     文件夹名
     * @return 新文件夹 UUID
     */
    public UUID createGroup(UUID parentId, String name) {
        UUID groupUuid = UUID.randomUUID();
        byte[] dek = new byte[32];
        vault.random().nextBytes(dek);
        // 父 DEK：子文件夹用父文件夹 DEK 包裹；顶层用 objects root DEK
        byte[] parentDek = (parentId != null && vault.folderDek(parentId) != null)
                ? vault.folderDek(parentId)
                : vault.dekForRole("objects");
        byte[] wrapped = wrap(dek, parentDek);
        Json.Node group = Json.obj();
        Json.put(group, "version", Json.of(1));
        Json.put(group, "type", Json.of("group"));
        Json.put(group, "name", Json.of(name));
        Json.put(group, "parent", parentId == null ? Json.ofNull() : Json.of(parentId.toString()));
        Json.put(group, "dek", Json.of(java.util.Base64.getEncoder().encodeToString(wrapped)));
        Json.put(group, "updateTimestamp", Json.of(nextTimestamp()));
        writeObject(groupUuid, group, parentId);
        vault.addFolderDek(groupUuid, dek);
        refresh();
        return groupUuid;
    }

    /** 用父 DEK 包裹一个 DEK（AES-GCM-SIV，nonce 随机）。 */
    private byte[] wrap(byte[] dek, byte[] parentDek) {
        byte[] encKey = com.flora.sanctum.crypto.impl.HkdfSha256.derive(parentDek, null, "sanctum-enc", 32);
        com.flora.sanctum.crypto.CipherCodec codec = new com.flora.sanctum.crypto.CipherCodec(encKey, parentDek, vault.random());
        return codec.encode(java.util.UUID.randomUUID(), dek, codec.makeKeyIdWith(parentDek));
    }

    /**
     * 换主密码：新 KEK 重新包裹三个顶层 root DEK 并重加密 root group 块，更新 manifest MAC。
     * 子文件夹 DEK 链不动（用父 DEK 包裹，根 DEK 未变），见设计 02。
     */
    public void changeMasterPassword(char[] newPassword, int memoryKiB, int iterations, int parallelism) {
        if (vault == null) {
            throw new IllegalStateException("not unlocked");
        }
        byte[] oldKek = vault.kek();
        // 新 salt + 新 KEK（salt 终身不变？设计 02 说 salt 终身不变，这里保留旧 salt 用新密码派生）
        // 设计：salt 终身不变，换主密码仅 KEK 变。故复用 manifest 的 salt 和参数。
        Manifest m = vault.manifest();
        Argon2Kdf kdf = new Argon2Kdf(m.salt(), m.memoryKiB(), m.iterations(), m.parallelism());
        byte[] newKek = kdf.derive(newPassword);
        try {
            // 重包三个 root group（用旧 KEK 解密块 + 解 DEK，用新 KEK 重加密）
            for (com.flora.sanctum.store.Block b : store.scan()) {
                if (!b.isCipher()) {
                    continue;
                }
                byte[] oldEnc = com.flora.sanctum.crypto.impl.HkdfSha256.derive(oldKek, null, "sanctum-enc", 32);
                com.flora.sanctum.crypto.CipherCodec oldCodec =
                        new com.flora.sanctum.crypto.CipherCodec(oldEnc, oldKek, vault.random());
                byte[] plain;
                try {
                    plain = oldCodec.decode(b.obfuscated()).plaintext;
                } catch (Exception e) {
                    continue; // 非 KEK 包裹（普通对象树内由父 DEK 包裹），跳过
                }
                Json.Node n = Json.parse(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
                if ("group".equals(n.str("type")) && n.str("role") != null) {
                    // 用旧 KEK 解出 DEK，用新 KEK 重包裹 + 重加密块
                    byte[] oldWrapped = java.util.Base64.getDecoder().decode(n.str("dek"));
                    byte[] dek = oldCodec.decode(oldWrapped).plaintext;
                    byte[] newWrapped = wrap(dek, newKek);
                    n = Json.parse(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
                    Json.put(n, "dek", Json.of(java.util.Base64.getEncoder().encodeToString(newWrapped)));
                    writeCipherBlockWith(b.uuid(), n, newKek);
                }
            }
            // 更新 manifest 的 MAC（用新 KEK）
            java.util.UUID manifestUuid = findManifestUuid();
            Manifest updated = new Manifest(m.version(), m.cryptoVersion(), m.kdf(), m.salt(),
                    m.memoryKiB(), m.iterations(), m.parallelism(), vault.clock().warehouseTime(), m.updateTimestamp(), new byte[0]);
            byte[] macKey = updated.manifestMacKey(newKek);
            byte[] mac = updated.computeMac(macKey, manifestUuid);
            Json.Node manifest = Json.obj();
            Json.put(manifest, "version", Json.of(updated.version()));
            Json.put(manifest, "type", Json.of("manifest"));
            Json.put(manifest, "cryptoVersion", Json.of(updated.cryptoVersion()));
            Json.put(manifest, "kdf", Json.of(updated.kdf()));
            Json.put(manifest, "salt", Json.of(java.util.Base64.getEncoder().encodeToString(updated.salt())));
            Json.Node params = Json.obj();
            Json.put(params, "m", Json.of(updated.memoryKiB()));
            Json.put(params, "i", Json.of(updated.iterations()));
            Json.put(params, "p", Json.of(updated.parallelism()));
            Json.put(manifest, "params", params);
            Json.put(manifest, "warehouseTime", Json.of(updated.warehouseTime()));
            Json.put(manifest, "updateTimestamp", Json.of(updated.updateTimestamp()));
            Json.put(manifest, "mac", Json.of(java.util.Base64.getEncoder().encodeToString(mac)));
            writeManifestPlaintextBlock(manifestUuid, manifest);
            // 更新 Vault 的 KEK 为新 KEK
            vault.replaceKek(newKek);
        } finally {
            java.util.Arrays.fill(newKek, (byte) 0);
            java.util.Arrays.fill(oldKek, (byte) 0);
        }
    }

    private void writeCipherBlockWith(java.util.UUID uuid, Json.Node payload, byte[] keyMaterial) {
        byte[] json = Json.stringify(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] encKey = com.flora.sanctum.crypto.impl.HkdfSha256.derive(keyMaterial, null, "sanctum-enc", 32);
        com.flora.sanctum.crypto.CipherCodec codec = new com.flora.sanctum.crypto.CipherCodec(encKey, keyMaterial, vault.random());
        byte[] block = codec.encode(uuid, json, codec.makeKeyIdWith(keyMaterial));
        store.put(uuid, block, new com.flora.sanctum.store.impl.RawCodec());
    }

    /**
     * 新建一个条目（含字段）。
     *
     * @param groupId  所属组 UUID（null=普通对象 root）
     * @param name     条目名
     * @param fields   字段名 → 明文值
     * @return 新条目 UUID
     */
    public UUID createEntry(UUID groupId, String name, Map<String, String> fields) {
        UUID entryUuid = UUID.randomUUID();
        long ts = nextTimestamp();
        Json.Node entry = Json.obj();
        Json.put(entry, "version", Json.of(1));
        Json.put(entry, "type", Json.of("entry"));
        Json.put(entry, "name", Json.of(name));
        Json.put(entry, "parent", groupId == null ? Json.ofNull() : Json.of(groupId.toString()));
        Json.put(entry, "updateTimestamp", Json.of(ts));
        writeObject(entryUuid, entry, groupId);
        // 字段各自独立对象，parent 指向条目
        for (Map.Entry<String, String> f : fields.entrySet()) {
            UUID fieldUuid = UUID.randomUUID();
            Json.Node field = Json.obj();
            Json.put(field, "version", Json.of(1));
            Json.put(field, "type", Json.of("field"));
            Json.put(field, "parent", Json.of(entryUuid.toString()));
            Json.put(field, "fieldName", Json.of(f.getKey()));
            Json.put(field, "value", Json.of(f.getValue()));
            Json.put(field, "updateTimestamp", Json.of(nextTimestamp()));
            writeObject(fieldUuid, field, groupId);
        }
        refresh();
        return entryUuid;
    }

    /** 读取条目。 */
    public Json.Node getEntry(UUID uuid) {
        return readObject(uuid);
    }

    /** 删除条目（含其字段）。 */
    public void deleteEntry(UUID uuid) {
        store.delete(uuid);
        for (UUID f : directory.childrenOf(uuid)) {
            store.delete(f);
        }
        refresh();
    }

    // ---- 图标 / SSH 密钥（见设计 05）----

    /** 新建自定义图标（用 icon root DEK 加密，parent 指向 icon root group）。 */
    public UUID createIcon(byte[] data, String format) {
        UUID iconUuid = UUID.randomUUID();
        Json.Node icon = Json.obj();
        Json.put(icon, "version", Json.of(1));
        Json.put(icon, "type", Json.of("icon"));
        Json.put(icon, "parent", Json.of(vault.rootGroupUuid("icon").toString()));
        Json.put(icon, "data", Json.of(java.util.Base64.getEncoder().encodeToString(data)));
        Json.put(icon, "format", Json.of(format));
        Json.put(icon, "updateTimestamp", Json.of(nextTimestamp()));
        byte[] dek = vault.dekForRole("icon");
        writeObjectWithDek(iconUuid, icon, dek);
        refresh();
        return iconUuid;
    }

    /** 新建 SSH 私钥（用 sshKey root DEK 加密，parent 指向 sshKey root group）。 */
    public UUID createSshKey(String name, String privateKeyPem) {
        UUID keyUuid = UUID.randomUUID();
        Json.Node key = Json.obj();
        Json.put(key, "version", Json.of(1));
        Json.put(key, "type", Json.of("sshKey"));
        Json.put(key, "parent", Json.of(vault.rootGroupUuid("sshKey").toString()));
        Json.put(key, "name", Json.of(name));
        Json.put(key, "privateKey", Json.of(privateKeyPem));
        Json.put(key, "updateTimestamp", Json.of(nextTimestamp()));
        byte[] dek = vault.dekForRole("sshKey");
        writeObjectWithDek(keyUuid, key, dek);
        refresh();
        return keyUuid;
    }

    /** 用指定 DEK 写对象（供 icon/sshKey 按 role 路由）。 */
    private void writeObjectWithDek(UUID uuid, Json.Node payload, byte[] dek) {
        byte[] json = Json.stringify(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] encKey = com.flora.sanctum.crypto.impl.HkdfSha256.derive(dek, null, "sanctum-enc", 32);
        com.flora.sanctum.crypto.CipherCodec codec = new com.flora.sanctum.crypto.CipherCodec(encKey, dek, vault.random());
        byte[] block = codec.encode(uuid, json, codec.makeKeyIdWith(dek));
        store.put(uuid, block, new com.flora.sanctum.store.impl.RawCodec());
    }


    /**
     * 收集垃圾：从根集合（manifest + 三个顶层 group + 顶层条目）出发，
     * 沿归属边(parent)与引用边(icon/keyRef)遍历，不可达的孤立块列入清单并软删除。
     * 返回被软删除的孤立块 uuid 列表。
     */
    // ---- GC / 搜索（见设计 04b"可达树"）----

    public java.util.List<UUID> collectGarbage() {
        if (vault == null) {
            throw new IllegalStateException("not unlocked");
        }
        java.util.List<Block> blocks = store.scan();
        java.util.Set<UUID> reachable = new java.util.HashSet<>();
        // 根：manifest + 顶层 group（parent==null）+ 顶层条目（parent==null）
        for (Block b : blocks) {
            if (b.isPlaintext()) {
                reachable.add(b.uuid()); // manifest
                continue;
            }
            Json.Node n = nodeOf(b);
            if (n == null) {
                continue;
            }
            String parent = n.str("parent");
            if (parent == null || "group".equals(n.str("type")) && parent.isEmpty()) {
                reachable.add(b.uuid()); // 顶层对象（parent==null）
            }
        }
        // 沿 parent 链 + 引用边扩展
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Block b : blocks) {
                if (reachable.contains(b.uuid())) {
                    continue;
                }
                Json.Node n = nodeOf(b);
                if (n == null) {
                    continue;
                }
                String parent = n.str("parent");
                String icon = n.str("icon");
                String keyRef = n.str("keyRef");
                if ((parent != null && reachable.contains(UUID.fromString(parent)))
                        || (icon != null && reachable.contains(UUID.fromString(icon)))
                        || (keyRef != null && isUuid(keyRef) && reachable.contains(UUID.fromString(keyRef)))) {
                    reachable.add(b.uuid());
                    progress = true;
                }
            }
        }
        // 不可达 = 孤立 → 软删除
        java.util.List<UUID> orphaned = new java.util.ArrayList<>();
        for (Block b : blocks) {
            if (!reachable.contains(b.uuid())) {
                store.delete(b.uuid());
                orphaned.add(b.uuid());
            }
        }
        refresh();
        return orphaned;
    }

    /** 按 uuid 查找对象（返回其负载 JSON；未找到返回 null）。 */
    public Json.Node search(UUID uuid) {
        return getEntry(uuid);
    }

    private boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Json.Node nodeOf(Block b) {
        byte[] plain = vault.resolve(b.obfuscated());
        if (plain == null) {
            return null;
        }
        try {
            return Json.parse(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private void writeObject(UUID uuid, Json.Node payload, UUID groupId) {
        byte[] json = Json.stringify(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] dek = resolveDekFor(groupId);
        byte[] encKey = com.flora.sanctum.crypto.impl.HkdfSha256.derive(dek, null, "sanctum-enc", 32);
        com.flora.sanctum.crypto.CipherCodec codec = new com.flora.sanctum.crypto.CipherCodec(encKey, dek, vault.random());
        byte[] block = codec.encode(uuid, json, codec.makeKeyIdWith(dek));
        store.put(uuid, block, new com.flora.sanctum.store.impl.RawCodec());
    }

    private Json.Node readObject(UUID uuid) {
        for (Block b : store.scan()) {
            if (b.uuid().equals(uuid)) {
                byte[] plain = vault.resolve(b.obfuscated());
                if (plain == null) {
                    return null;
                }
                return Json.parse(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return null;
    }

    /** 找加密归属 DEK：条目/字段若在子文件夹下用该文件夹 DEK，否则用 objects root（见设计 05）。 */
    private byte[] resolveDekFor(UUID groupId) {
        if (groupId != null && vault.folderDek(groupId) != null) {
            return vault.folderDek(groupId);
        }
        return vault.dekForRole("objects");
    }

    /** 计算本次写入的 updateTimestamp（仓库时间戳规则：max(会话偏移+锚点, 全库最大)）。 */
    private long nextTimestamp() {
        long maxExisting = 1;
        if (directory != null) {
            for (Json.Node n : directory.objects.values()) {
                Long t = n.lng("updateTimestamp");
                if (t != null && t > maxExisting) {
                    maxExisting = t;
                }
            }
        }
        return vault.clock().nextTimestamp(maxExisting);
    }

    /** 内存目录（解锁后构建）。 */
    public static final class Directory {
        private final Map<UUID, Json.Node> objects = new LinkedHashMap<>();
        private final List<byte[]> rootDeks = new ArrayList<>();

        private Directory() {
        }

        static Directory build(Vault vault) {
            Directory d = new Directory();
            // root DEK 已在解锁时提取
            d.rootDeks.addAll(vault.rootDeks());
            for (Block b : vault.store().scan()) {
                byte[] plain = vault.resolve(b.obfuscated());
                if (plain == null) {
                    continue;
                }
                try {
                    Json.Node n = Json.parse(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
                    d.objects.put(b.uuid(), n);
                } catch (Exception ignore) {
                    // 无法解析的块跳过
                }
            }
            return d;
        }

        public List<byte[]> rootDeks() {
            return rootDeks;
        }

        public List<UUID> childrenOf(UUID parent) {
            List<UUID> out = new ArrayList<>();
            for (Map.Entry<UUID, Json.Node> e : objects.entrySet()) {
                String p = e.getValue().str("parent");
                if (p != null && p.equals(parent.toString())) {
                    out.add(e.getKey());
                }
            }
            return out;
        }
    }
}
