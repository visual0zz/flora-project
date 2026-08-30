package com.flora.sanctum.core.io.importer;

import com.flora.sanctum.core.model.tree.GroupNode;
import com.flora.sanctum.core.model.tree.IconTree;
import com.flora.sanctum.core.model.tree.ObjectTree;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 一次导入的上下文：目标树、主密码/密钥文件、进度回调、可选的目标分组。
 * <p>主密码以 {@code char[]} 持有，导入结束后调用 {@link #clearSecrets()} 清零。</p>
 */
public final class ImportContext {

    private final ObjectTree tree;
    private final char[] password;
    private final Path keyFile;
    private final ImportListener listener;
    private final GroupNode targetGroup;
    private final IconTree iconTree;

    private ImportContext(Builder b) {
        this.tree = Objects.requireNonNull(b.tree, "tree");
        this.password = b.password;
        this.keyFile = b.keyFile;
        this.listener = b.listener == null ? ImportListeners.noop() : b.listener;
        this.targetGroup = b.targetGroup;
        this.iconTree = b.iconTree;
    }

    public ObjectTree tree() {
        return tree;
    }

    /** 图标树（用于导入时复制自定义图标 / 引用内置图标）。非 KDBX 导入或测试可为 null。 */
    public IconTree iconTree() {
        return iconTree;
    }

    /** 主密码（可能为 null，仅用密钥文件时）。 */
    public char[] password() {
        return password;
    }

    /** 密钥文件路径（可能为 null）。 */
    public Path keyFile() {
        return keyFile;
    }

    public ImportListener listener() {
        return listener;
    }

    /** 导入目标分组；null 表示由导入器自建顶层分组。 */
    public GroupNode targetGroup() {
        return targetGroup;
    }

    /** 清零主密码，避免驻留内存。 */
    public void clearSecrets() {
        if (password != null) {
            java.util.Arrays.fill(password, '\0');
        }
    }

    public static Builder builder(ObjectTree tree) {
        return new Builder(tree);
    }

    public static final class Builder {
        private final ObjectTree tree;
        private char[] password;
        private Path keyFile;
        private ImportListener listener;
        private GroupNode targetGroup;
        private IconTree iconTree;

        private Builder(ObjectTree tree) {
            this.tree = tree;
        }

        public Builder password(char[] password) {
            this.password = password;
            return this;
        }

        public Builder keyFile(Path keyFile) {
            this.keyFile = keyFile;
            return this;
        }

        public Builder listener(ImportListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder targetGroup(GroupNode targetGroup) {
            this.targetGroup = targetGroup;
            return this;
        }

        /** 图标树（用于导入时复制自定义图标 / 引用内置图标）。可为 null。 */
        public Builder iconTree(IconTree iconTree) {
            this.iconTree = iconTree;
            return this;
        }

        public ImportContext build() {
            return new ImportContext(this);
        }
    }
}
