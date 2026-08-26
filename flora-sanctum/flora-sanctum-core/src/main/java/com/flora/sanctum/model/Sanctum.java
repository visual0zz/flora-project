package com.flora.sanctum.model;
import com.flora.sanctum.model.tree.*;
import com.flora.sanctum.model.vault.*;
import com.flora.sanctum.model.impl.*;

import com.flora.sanctum.store.ObjectStore;
import com.flora.sanctum.store.impl.MarkdownObjectStore;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * 密码库门面（对外主入口）。
 * <p>
 * 结构：{@code Sanctum = Vault(密钥状态) + LibraryConfig(配置数据) + List<DataTree>(数据树)}。
 * 打开/解锁/关闭由门面负责；新建/编辑/删除等操作由数据树节点承担（见设计 05"数据结构树化"）。
 */
public final class Sanctum implements AutoCloseable {

    private final Path root;
    private final ObjectStore store;

    private Vault vault;
    private TreeContext context;
    private LibraryConfig config;
    private List<DataTree> trees;

    private Sanctum(Path root) {
        this.root = root;
        this.store = new MarkdownObjectStore(root);
    }

    /** 打开（不锁定）。 */
    public static Sanctum open(Path root) {
        return new Sanctum(root);
    }

    /** 新建并解锁（默认高安全档 Argon2id 参数）。 */
    public static Sanctum createAndUnlock(Path root, char[] masterPassword) {
        return createAndUnlock(root, masterPassword,
                com.flora.sanctum.crypto.Argon2Kdf.DEFAULT_MEMORY_KIB,
                com.flora.sanctum.crypto.Argon2Kdf.DEFAULT_ITERATIONS,
                com.flora.sanctum.crypto.Argon2Kdf.DEFAULT_PARALLELISM);
    }

    /**
     * 新建并解锁，显式指定 Argon2id 参数（内存 KiB / 迭代次数 / 并行度）。
     * 供新建库时由用户自定义 KDF 强度；参数会被持久化到 manifest，解锁时自动读取。
     */
    public static Sanctum createAndUnlock(Path root, char[] masterPassword,
                                          int memoryKiB, int iterations, int parallelism) {
        Sanctum s = new Sanctum(root);
        new VaultCreator(s.store).create(masterPassword, memoryKiB, iterations, parallelism);
        s.unlock(masterPassword);
        return s;
    }

    /** 解锁：加载 manifest、KEK、root DEK、构建配置/四棵数据树。 */
    public void unlock(char[] masterPassword) {
        this.vault = new VaultUnlocker(store).unlock(masterPassword);
        this.context = new TreeContext(store, vault);
        this.config = new LibraryConfig(context);
        this.trees = List.of(
                new ObjectTree(context),
                new IconTree(context),
                new SshKeyTree(context),
                new RemoteTree(context));
    }

    public void lock() {
        if (vault != null) {
            vault.clearSecrets();
        }
        this.vault = null;
        this.context = null;
        this.config = null;
        this.trees = null;
    }

    /** 关闭库：锁定（会话时间戳锚点不持久化，见 02"仓库时间戳"）。 */
    @Override
    public void close() {
        if (vault == null) {
            return;
        }
        lock();
    }

    public boolean isUnlocked() {
        return vault != null;
    }

    /** 解锁后的密钥状态（model 包内部使用；app 经数据树访问，不直接碰密钥状态）。 */
    Vault vault() {
        return vault;
    }

    public Path root() {
        return root;
    }

    /** 仓库唯一根对象 uuid（未解锁返回 null）。 */
    public UUID rootGroupUuid() {
        return vault == null ? null : vault.rootGroupUuid(RootTag.DATA);
    }

    /** 配置数据（远端配置等）。 */
    public LibraryConfig config() {
        return config;
    }

    /** 四棵数据树（密码库对象树 / ICON / SSH_KEY / REMOTE）。 */
    public List<DataTree> trees() {
        return trees;
    }

    /** 按展示区段取数据树。 */
    @SuppressWarnings("unchecked")
    public <T extends DataTree> T tree(ViewNodeType category) {
        for (DataTree t : trees) {
            if (t.category() == category) {
                return (T) t;
            }
        }
        return null;
    }

    public ObjectTree objectTree() {
        return tree(ViewNodeType.PASSWORD);
    }

    public IconTree iconTree() {
        return tree(ViewNodeType.ICON);
    }

    public SshKeyTree sshKeyTree() {
        return tree(ViewNodeType.SSH_KEY);
    }

    public RemoteTree remoteTree() {
        return tree(ViewNodeType.REMOTE);
    }

    /** 跨树按 uuid 查找节点；未找到返回 null。 */
    public TreeNode findNode(UUID uuid) {
        for (DataTree t : trees) {
            TreeNode n = t.find(uuid);
            if (n != null) {
                return n;
            }
        }
        return null;
    }

    // ---- 委托操作（由独立组件承担） ----

    /** 换主密码（委托 MasterKeyRotator，见设计 02）。 */
    public void changeMasterPassword(char[] newPassword, int memoryKiB, int iterations, int parallelism) {
        new MasterKeyRotator(context).rotate(newPassword, memoryKiB, iterations, parallelism);
    }

    /** 收集垃圾（委托 GarbageCollector），返回被删除的孤立块 uuid。 */
    public List<UUID> collectGarbage() {
        return new GarbageCollector(context).collect();
    }

    /** 垃圾桶视图：识别手动删除/不可达/不可解锁三类异常节点（见设计 idea20260826-sanctum-trash）。 */
    public TrashView trash() {
        return new TrashClassifier(context).classify();
    }

    /** 取某 group 的 DEK（null 若未发现）。 */
    byte[] groupDek(UUID groupUuid) {
        return vault == null ? null : vault.groupDek(groupUuid);
    }

    ObjectStore store() {
        return store;
    }
}
