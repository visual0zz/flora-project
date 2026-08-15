package com.flora.sanctum.model;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonNull;
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
    private final ObjectStore store;
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
        JsonObject manifest = new JsonObject();
        manifest.put("version", updated.version());
        manifest.put("type", "manifest");
        manifest.put("cryptoVersion", updated.cryptoVersion());
        manifest.put("kdf", updated.kdf());
        manifest.put("salt", java.util.Base64.getEncoder().encodeToString(updated.salt()));
        JsonObject params = new JsonObject();
        params.put("m", updated.memoryKiB());
        params.put("i", updated.iterations());
        params.put("p", updated.parallelism());
        manifest.put("params", params);
        manifest.put("warehouseTime", newWarehouseTime);
        manifest.put("updateTimestamp", updated.updateTimestamp());
        manifest.put("mac", java.util.Base64.getEncoder().encodeToString(mac));
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
                    JsonObject n = JsonUtil.parseObject(new String(payload, java.nio.charset.StandardCharsets.UTF_8));
                    if ("manifest".equals(n.getString("type"))) {
                        return b.uuid();
                    }
                } catch (Exception ignore) {
                }
            }
        }
        throw new IllegalStateException("manifest not found");
    }

    private void writeManifestPlaintextBlock(java.util.UUID uuid, JsonObject payload) {
        byte[] json = JsonUtil.toJsonString(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] block = new byte[6 + 16 + json.length];
        System.arraycopy(com.flora.sanctum.crypto.impl.Envelope.MAGIC, 0, block, 0, 4);
        block[4] = com.flora.sanctum.crypto.impl.Envelope.VERSION_1;
        block[5] = com.flora.sanctum.crypto.impl.Envelope.FLAG_PLAINTEXT;
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(block, 6, 16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        System.arraycopy(json, 0, block, 22, json.length);
        byte xor = vault.random().nextByte();
        byte[] obf = com.flora.sanctum.store.BlockHeader.obfuscate(block, xor);
        // 经 ObjectStore 落盘（codec null = 裸明文写，见 04）
        store.put(uuid, obf, null);
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

    ObjectStore store() {
        return store;
    }

    /** 列出库中全部对象 UUID（领域方法，UI/CLI 用，不直接碰 store）。 */
    public java.util.List<UUID> listObjectUuids() {
        return store.list();
    }

    /** 库中对象数。 */
    public int objectCount() {
        return store.list().size();
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
        JsonObject group = new JsonObject();
        group.put("version", 1);
        group.put("type", "group");
        group.put("name", name);
        group.put("parent", parentId == null ? JsonNull.INSTANCE : parentId.toString());
        group.put("dek", java.util.Base64.getEncoder().encodeToString(wrapped));
        group.put("updateTimestamp", nextTimestamp());
        writeObject(groupUuid, group, parentId);
        vault.addFolderDek(groupUuid, dek);
        refresh();
        return groupUuid;
    }

    /** 新建文件夹（含自定义图标引用）。 */
    public UUID createGroup(UUID parentId, String name, UUID iconUuid) {
        UUID groupUuid = createGroup(parentId, name);
        if (iconUuid != null) {
            JsonObject group = readObject(groupUuid);
            if (group != null) {
                group.put("icon", iconUuid.toString());
                writeObject(groupUuid, group, parentId);
                refresh();
            }
        }
        return groupUuid;
    }

    /**
     * 新建远端配置（kind:remote 字段，value 含 url + keyRef，见 05"remote"）。
     * 置于 objects root 之下，用 objects root DEK 加密。
     */
    public UUID createRemote(String name, String url, String keyRef) {
        UUID remoteUuid = UUID.randomUUID();
        JsonObject remote = new JsonObject();
        remote.put("version", 1);
        remote.put("type", "field");
        remote.put("parent", vault.rootGroupUuid("objects").toString());
        remote.put("fieldName", name);
        remote.put("kind", "remote");
        JsonObject value = new JsonObject();
        value.put("url", url);
        if (keyRef != null) {
            value.put("keyRef", keyRef);
        }
        remote.put("value", value);
        remote.put("updateTimestamp", nextTimestamp());
        byte[] dek = vault.dekForRole("objects");
        writeObjectWithDek(remoteUuid, remote, dek);
        refresh();
        return remoteUuid;
    }

    /** 用父 DEK 包裹一个 DEK（AES-GCM-SIV，nonce 随机）。 */
    private byte[] wrap(byte[] dek, byte[] parentDek) {
        byte[] encKey = com.flora.sanctum.crypto.KeyDerivation.encKey(parentDek);
        com.flora.sanctum.crypto.impl.CipherCodec codec = new com.flora.sanctum.crypto.impl.CipherCodec(encKey, parentDek, vault.random());
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
        // 用传入的新 KDF 参数派生新 KEK（salt 终身不变，参数可升级，见 02"KDF 参数可配置"）
        Argon2Kdf kdf = new Argon2Kdf(m.salt(), memoryKiB, iterations, parallelism);
        byte[] newKek = kdf.derive(newPassword);
        try {
            // 重包三个 root group（用旧 KEK 解密块 + 解 DEK，用新 KEK 重加密）
            for (com.flora.sanctum.store.Block b : store.scan()) {
                if (!b.isCipher()) {
                    continue;
                }
                byte[] oldEnc = com.flora.sanctum.crypto.KeyDerivation.encKey(oldKek);
                com.flora.sanctum.crypto.impl.CipherCodec oldCodec =
                        new com.flora.sanctum.crypto.impl.CipherCodec(oldEnc, oldKek, vault.random());
                byte[] plain;
                try {
                    plain = oldCodec.decode(b.obfuscated()).plaintext;
                } catch (Exception e) {
                    continue; // 非 KEK 包裹（普通对象树内由父 DEK 包裹），跳过
                }
                JsonObject n = JsonUtil.parseObject(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
                if ("group".equals(n.getString("type")) && n.getString("role") != null) {
                    // 用旧 KEK 解出 DEK，用新 KEK 重包裹 + 重加密块
                    byte[] oldWrapped = java.util.Base64.getDecoder().decode(n.getString("dek"));
                    byte[] dek = oldCodec.decode(oldWrapped).plaintext;
                    byte[] newWrapped = wrap(dek, newKek);
                    n = JsonUtil.parseObject(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
                    n.put("dek", java.util.Base64.getEncoder().encodeToString(newWrapped));
                    writeCipherBlockWith(b.uuid(), n, newKek);
                }
            }
            // 更新 manifest 的 MAC（用新 KEK）
            java.util.UUID manifestUuid = findManifestUuid();
            Manifest updated = new Manifest(m.version(), m.cryptoVersion(), m.kdf(), m.salt(),
                    memoryKiB, iterations, parallelism, vault.clock().warehouseTime(), m.updateTimestamp(), new byte[0]);
            byte[] macKey = updated.manifestMacKey(newKek);
            byte[] mac = updated.computeMac(macKey, manifestUuid);
            JsonObject manifest = new JsonObject();
            manifest.put("version", updated.version());
            manifest.put("type", "manifest");
            manifest.put("cryptoVersion", updated.cryptoVersion());
            manifest.put("kdf", updated.kdf());
            manifest.put("salt", java.util.Base64.getEncoder().encodeToString(updated.salt()));
            JsonObject params = new JsonObject();
            params.put("m", updated.memoryKiB());
            params.put("i", updated.iterations());
            params.put("p", updated.parallelism());
            manifest.put("params", params);
            manifest.put("warehouseTime", updated.warehouseTime());
            manifest.put("updateTimestamp", updated.updateTimestamp());
            manifest.put("mac", java.util.Base64.getEncoder().encodeToString(mac));
            writeManifestPlaintextBlock(manifestUuid, manifest);
            // 更新内存 manifest（含新 KDF 参数），供后续 close/写回使用新值
            vault.replaceManifest(updated);
            // 更新 Vault 的 KEK 为新 KEK
            vault.replaceKek(newKek);
        } finally {
            java.util.Arrays.fill(newKek, (byte) 0);
            java.util.Arrays.fill(oldKek, (byte) 0);
        }
    }

    private void writeCipherBlockWith(java.util.UUID uuid, JsonObject payload, byte[] keyMaterial) {
        writeCipherBlock(uuid, payload, keyMaterial);
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
        return createEntry(groupId, name, fields, null, null);
    }

    /**
     * 新建条目（含图标引用）。
     *
     * @param iconId    内置图标集索引（可 null）
     * @param iconUuid  自定义图标对象 UUID（引用，非归属；可 null，优先于 iconId）
     */
    public UUID createEntry(UUID groupId, String name, Map<String, String> fields, Integer iconId, UUID iconUuid) {
        UUID entryUuid = UUID.randomUUID();
        long ts = nextTimestamp();
        JsonObject entry = new JsonObject();
        entry.put("version", 1);
        entry.put("type", "entry");
        entry.put("name", name);
        entry.put("parent", groupId == null ? JsonNull.INSTANCE : groupId.toString());
        if (iconId != null) {
            entry.put("iconId", iconId);
        }
        if (iconUuid != null) {
            entry.put("icon", iconUuid.toString());
        }
        entry.put("updateTimestamp", ts);
        writeObject(entryUuid, entry, groupId);
        // 字段各自独立对象，parent 指向条目
        for (Map.Entry<String, String> f : fields.entrySet()) {
            UUID fieldUuid = UUID.randomUUID();
            JsonObject field = new JsonObject();
            field.put("version", 1);
            field.put("type", "field");
            field.put("parent", entryUuid.toString());
            field.put("fieldName", f.getKey());
            field.put("value", f.getValue());
            field.put("updateTimestamp", nextTimestamp());
            writeObject(fieldUuid, field, groupId);
        }
        refresh();
        return entryUuid;
    }

    /** 读取条目。 */
    public JsonObject getEntry(UUID uuid) {
        return readObject(uuid);
    }

    /**
     * 导出加密归档（见设计 03"备份"）：把库根全部密文块文件打包为 zip。
     * 块已 AES-GCM-SIV 加密，归档保持密文；恢复即解压回库根。
     */
    public void exportArchive(java.nio.file.Path outZip) throws java.io.IOException {
        try (java.util.zip.ZipOutputStream zos =
                     new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(outZip))) {
            for (com.flora.sanctum.store.Block b : store.scan()) {
                zos.putNextEntry(new java.util.zip.ZipEntry(b.file().getFileName().toString()));
                zos.write(java.nio.file.Files.readAllBytes(b.file()));
                zos.closeEntry();
            }
        }
    }

    /** 删除条目（含其字段）。 */
    public void deleteEntry(UUID uuid) {
        store.delete(uuid);
        for (UUID f : directory.childrenOf(uuid)) {
            store.delete(f);
        }
        refresh();
    }

    // ---- 自定义字段（含 kind：totp / externalKey / remote 等，见 05）----

    /** 在条目下创建带 kind 的字段。 */
    public UUID createFieldWithKind(UUID entryUuid, UUID groupId, String fieldName, String value, String kind) {
        UUID fieldUuid = UUID.randomUUID();
        JsonObject field = new JsonObject();
        field.put("version", 1);
        field.put("type", "field");
        field.put("parent", entryUuid.toString());
        field.put("fieldName", fieldName);
        field.put("value", value);
        if (kind != null) {
            field.put("kind", kind);
        }
        field.put("updateTimestamp", nextTimestamp());
        writeObject(fieldUuid, field, groupId);
        refresh();
        return fieldUuid;
    }

    /** 从 kind:totp 字段生成当前 TOTP 验证码（种子为 value，见 02"TOTP"）。 */
    public String totpCode(UUID fieldUuid) {
        JsonObject field = readObject(fieldUuid);
        if (field == null || !"totp".equals(field.getString("kind"))) {
            throw new IllegalArgumentException("not a totp field");
        }
        byte[] secret = com.flora.root.codec.Base32.decode(field.getString("value"));
        return com.flora.sanctum.crypto.Totp.generate(secret, 6, 30);
    }

    // ---- 图标 / SSH 密钥（见设计 05）----

    /** 新建自定义图标（用 icon root DEK 加密，parent 指向 icon root group）。 */
    public UUID createIcon(byte[] data, String format) {
        UUID iconUuid = UUID.randomUUID();
        JsonObject icon = new JsonObject();
        icon.put("version", 1);
        icon.put("type", "icon");
        icon.put("parent", vault.rootGroupUuid("icon").toString());
        icon.put("data", java.util.Base64.getEncoder().encodeToString(data));
        icon.put("format", format);
        icon.put("updateTimestamp", nextTimestamp());
        byte[] dek = vault.dekForRole("icon");
        writeObjectWithDek(iconUuid, icon, dek);
        refresh();
        return iconUuid;
    }

    /** 新建 SSH 私钥（用 sshKey root DEK 加密，parent 指向 sshKey root group）。 */
    public UUID createSshKey(String name, String privateKeyPem) {
        UUID keyUuid = UUID.randomUUID();
        JsonObject key = new JsonObject();
        key.put("version", 1);
        key.put("type", "sshKey");
        key.put("parent", vault.rootGroupUuid("sshKey").toString());
        key.put("name", name);
        key.put("privateKey", privateKeyPem);
        key.put("updateTimestamp", nextTimestamp());
        byte[] dek = vault.dekForRole("sshKey");
        writeObjectWithDek(keyUuid, key, dek);
        refresh();
        return keyUuid;
    }

    /** 用指定 DEK 写对象（供 icon/sshKey 按 role 路由）。 */
    private void writeObjectWithDek(UUID uuid, JsonObject payload, byte[] dek) {
        writeCipherBlock(uuid, payload, dek);
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
            JsonObject n = nodeOf(b);
            if (n == null) {
                continue;
            }
            String parent = n.getString("parent");
            if (parent == null || "group".equals(n.getString("type")) && parent.isEmpty()) {
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
                JsonObject n = nodeOf(b);
                if (n == null) {
                    continue;
                }
                String parent = n.getString("parent");
                String icon = n.getString("icon");
                String keyRef = n.getString("keyRef");
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
    public JsonObject search(UUID uuid) {
        return getEntry(uuid);
    }

    /** 按类型查找对象（type: group/entry/field/sshKey/icon 等）。 */
    public java.util.List<JsonObject> searchByType(String type) {
        java.util.List<JsonObject> out = new java.util.ArrayList<>();
        for (com.flora.sanctum.store.Block b : store.scan()) {
            JsonObject n = nodeOf(b);
            if (n != null && type.equals(n.getString("type"))) {
                out.add(n);
            }
        }
        return out;
    }

    /** 查找某 uuid 的全部副本（块位置列表，用于去重/恢复/核查，见 04b"search"）。 */
    public java.util.List<com.flora.sanctum.store.Block> findCopies(UUID uuid) {
        java.util.List<com.flora.sanctum.store.Block> out = new java.util.ArrayList<>();
        for (com.flora.sanctum.store.Block b : store.scan()) {
            if (b.uuid().equals(uuid)) {
                out.add(b);
            }
        }
        return out;
    }

    private boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private JsonObject nodeOf(Block b) {
        byte[] plain = vault.resolve(b.obfuscated());
        if (plain == null) {
            return null;
        }
        try {
            return JsonUtil.parseObject(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private void writeObject(UUID uuid, JsonObject payload, UUID groupId) {
        writeCipherBlock(uuid, payload, resolveDekFor(groupId));
    }

    /** 统一密文写块：用指定 DEK 加密负载并经 ObjectStore 落盘（复用 CipherCodecAdapter）。 */
    private void writeCipherBlock(UUID uuid, JsonObject payload, byte[] dek) {
        byte[] json = JsonUtil.toJsonString(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] encKey = com.flora.sanctum.crypto.KeyDerivation.encKey(dek);
        com.flora.sanctum.crypto.impl.CipherCodec codec = new com.flora.sanctum.crypto.impl.CipherCodec(encKey, dek, vault.random());
        store.put(uuid, json, new com.flora.sanctum.store.impl.CipherCodecAdapter(codec, uuid));
    }

    private JsonObject readObject(UUID uuid) {
        for (Block b : store.scan()) {
            if (b.uuid().equals(uuid)) {
                byte[] plain = vault.resolve(b.obfuscated());
                if (plain == null) {
                    return null;
                }
                return JsonUtil.parseObject(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
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
            for (JsonObject n : directory.objects.values()) {
                Long t = n.getLong("updateTimestamp");
                if (t != null && t > maxExisting) {
                    maxExisting = t;
                }
            }
        }
        return vault.clock().nextTimestamp(maxExisting);
    }

    /** 内存目录（解锁后构建）。 */
    public static final class Directory {
        private final Map<UUID, JsonObject> objects = new LinkedHashMap<>();
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
                    JsonObject n = JsonUtil.parseObject(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
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
            for (Map.Entry<UUID, JsonObject> e : objects.entrySet()) {
                String p = e.getValue().getString("parent");
                if (p != null && p.equals(parent.toString())) {
                    out.add(e.getKey());
                }
            }
            return out;
        }
    }
}
