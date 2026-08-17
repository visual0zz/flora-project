package com.flora.sanctum.model;
import com.flora.sanctum.model.tree.*;
import com.flora.sanctum.model.vault.*;
import com.flora.sanctum.model.impl.*;

import com.flora.sanctum.crypto.impl.SecureRandomSource;
import com.flora.sanctum.store.ObjectStore;
import com.flora.sanctum.store.impl.MarkdownObjectStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * 密码库门面（对外主入口）。
 * <p>
 * 结构：{@code Sanctum = Metadata(元数据) + LibraryConfig(配置数据) + List<DataTree>(数据树)}。
 * 打开/解锁/关闭由门面负责；新建/编辑/删除等操作由数据树节点承担（见设计 05"数据结构树化"）。
 */
public final class Sanctum implements AutoCloseable {

    private final Path root;
    private final ObjectStore store;
    private final ManifestStore manifestStore;

    private Vault vault;
    private TreeContext context;
    private Metadata metadata;
    private LibraryConfig config;
    private List<DataTree> trees;

    private Sanctum(Path root) {
        this.root = root;
        this.store = new MarkdownObjectStore(root);
        this.manifestStore = new ManifestStore(store, new SecureRandomSource());
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

    /** 解锁：加载 manifest、KEK、root DEK、构建元数据/配置/四棵数据树。 */
    public void unlock(char[] masterPassword) {
        this.vault = new VaultUnlocker(store).unlock(masterPassword);
        this.context = new TreeContext(store, vault);
        this.metadata = Metadata.from(vault.manifest());
        com.flora.sanctum.store.Block manifestBlock = manifestStore.findBlock();
        if (manifestBlock != null) {
            this.metadata = this.metadata.withBlock(manifestBlock.file(), manifestBlock.line());
        }
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
        this.metadata = null;
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

    /** 元数据（版本/KDF 参数/salt/仓库时间戳）。 */
    public Metadata metadata() {
        return metadata;
    }

    /** 配置数据（远端配置等）。 */
    public LibraryConfig config() {
        return config;
    }

    /** 四棵数据树（DATA/ICON/SSH_KEY/REMOTE）。 */
    public List<DataTree> trees() {
        return trees;
    }

    /** 按根概念取数据树。 */
    @SuppressWarnings("unchecked")
    public <T extends DataTree> T tree(RootTag tag) {
        for (DataTree t : trees) {
            if (t.tag() == tag) {
                return (T) t;
            }
        }
        return null;
    }

    public ObjectTree objectTree() {
        return tree(RootTag.DATA);
    }

    public IconTree iconTree() {
        return tree(RootTag.ICON);
    }

    public SshKeyTree sshKeyTree() {
        return tree(RootTag.SSH_KEY);
    }

    public RemoteTree remoteTree() {
        return tree(RootTag.REMOTE);
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

    /** 收集垃圾（委托 GarbageCollector），返回被软删除的孤立块 uuid。 */
    public List<UUID> collectGarbage() {
        return new GarbageCollector(context).collect();
    }

    /** 导出加密归档（委托 ArchiveExporter，见设计 03"备份"）。 */
    public void exportArchive(Path outZip) throws IOException {
        new ArchiveExporter(store).export(outZip);
    }

    // ---- 兼容小工具 ----

    /** 库中对象数。 */
    public int objectCount() {
        return store.list().size();
    }

    /** 取某文件夹的 DEK（null 若未发现）。 */
    byte[] folderDek(UUID groupUuid) {
        return vault == null ? null : vault.folderDek(groupUuid);
    }

    ObjectStore store() {
        return store;
    }
}
