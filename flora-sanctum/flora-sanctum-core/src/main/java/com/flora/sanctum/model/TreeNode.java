package com.flora.sanctum.model;

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

    /** 节点类型（group/entry/field/icon/sshKey/remote）。 */
    public abstract String type();

    /** 节点负载（内存图读取）。 */
    public JsonObject data() {
        return ctx().read(uuid);
    }

    /** 父对象标识：父 uuid 或根概念 tag。 */
    public String parent() {
        JsonObject d = data();
        return d == null ? null : d.getString("parent");
    }

    /** 节点是否仍存在。 */
    public boolean exists() {
        return data() != null;
    }

    /** 删除节点（软删除，存储层处理）。 */
    public void delete() {
        ctx().delete(uuid);
    }
}
