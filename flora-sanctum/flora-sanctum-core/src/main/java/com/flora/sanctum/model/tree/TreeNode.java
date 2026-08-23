package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.UUID;

/**
 * 数据树节点抽象基类（树上的新建/编辑/删除操作载体）。
 * <p>
 * 节点持有所属树与 uuid，读负载经 {@link TreeContext}；写操作由具体节点类型提供
 * （如 {@code GroupNode.createEntry}、{@code FieldNode.updateValue}）。
 */
public abstract class TreeNode {

    private final UUID uuid;
    private final DataTree tree;

    protected TreeNode(UUID uuid, DataTree tree) {
        this.uuid = uuid;
        this.tree = tree;
    }

    public UUID uuid() {
        return uuid;
    }

    public DataTree tree() {
        return tree;
    }

    protected TreeContext ctx() {
        return tree.context();
    }

    /** 节点类型（ROOT/GROUP/ENTRY/FIELD/ICON/SSH_KEY/REMOTE 等）。 */
    public abstract NodeType type();

    /** 节点负载（内存图读取）。 */
    public JsonObject data() {
        return ctx().read(uuid);
    }

    /** 该对象在存储中的原始块（物理位置 + base58，供审计/去重/恢复）；不存在返回 null。 */
    public com.flora.sanctum.store.Block block() {
        return ctx().blockOf(uuid);
    }

    /** 原始块所在文件（不存在返回 null）。 */
    public java.nio.file.Path file() {
        com.flora.sanctum.store.Block b = block();
        return b == null ? null : b.file();
    }

    /** 原始块所在行号（不存在返回 -1）。 */
    public long line() {
        com.flora.sanctum.store.Block b = block();
        return b == null ? -1 : b.line();
    }

    /** 父对象标识：父 uuid 或根概念 tag。 */
    public String parent() {
        JsonObject d = data();
        return d == null ? null : d.getString("parent");
    }

    /** 删除节点（物理删除对应存储块）。 */
    public void delete() {
        ctx().delete(uuid);
    }
}
