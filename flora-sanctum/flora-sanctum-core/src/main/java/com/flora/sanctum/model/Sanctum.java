package com.flora.sanctum.model;

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
public final class Sanctum {

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
        this.vault = null;
        this.directory = null;
    }

    public boolean isUnlocked() {
        return vault != null;
    }

    public Vault vault() {
        return vault;
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
     * 新建一个条目（含字段）。
     *
     * @param groupId  所属组 UUID（null=普通对象 root）
     * @param name     条目名
     * @param fields   字段名 → 明文值
     * @return 新条目 UUID
     */
    public UUID createEntry(UUID groupId, String name, Map<String, String> fields) {
        UUID entryUuid = UUID.randomUUID();
        Json.Node entry = Json.obj();
        Json.put(entry, "version", Json.of(1));
        Json.put(entry, "type", Json.of("entry"));
        Json.put(entry, "name", Json.of(name));
        Json.put(entry, "parent", groupId == null ? Json.ofNull() : Json.of(groupId.toString()));
        Json.put(entry, "updateTimestamp", Json.of(System.currentTimeMillis()));
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
            Json.put(field, "updateTimestamp", Json.of(System.currentTimeMillis()));
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

    // ---- 内部 ----

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

    /** 找一个可用 DEK（简化：用第一个 root DEK；生产按所属 group 精确路由）。 */
    private byte[] resolveDekFor(UUID groupId) {
        java.util.List<byte[]> deks = vault.rootDeks();
        if (deks.isEmpty()) {
            throw new IllegalStateException("no dek available");
        }
        return deks.get(0);
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
