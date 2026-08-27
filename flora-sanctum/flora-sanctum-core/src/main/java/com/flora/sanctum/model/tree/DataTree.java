package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 数据树容器（Sanctum = 元数据 + 配置数据 + List&lt;数据树&gt;）。
 * <p>
 * 每棵树对应一个节点类型分类（GROUP 树/ICON 树/SSH_KEY 树/REMOTE 树），承载该分类下
 * 的对象节点；节点负责新建/编辑/删除等操作（见设计 05"数据结构树化"）。
 * 所有树的顶层节点 parent 均指向仓库唯一根对象 uuid（单根模型）。
 */
public abstract class DataTree {

    private final ViewNodeType category;
    private final TreeContext ctx;

    protected DataTree(ViewNodeType category, TreeContext ctx) {
        this.category = category;
        this.ctx = ctx;
    }

    /** 树分类（展示区段，代表该树承载的节点展示归属）。 */
    public ViewNodeType category() {
        return category;
    }

    public TreeContext context() {
        return ctx;
    }

    /** 该树的类型集合（子类声明归属的存储类型）。 */
    protected abstract boolean belongsTo(StoredNodeType type, String kind);

    /** 按 uuid 查找本树节点；不属于本树返回 null。 */
    public abstract TreeNode find(UUID uuid);

    /** 本树全部节点（按类型过滤）。 */
    public List<TreeNode> nodes() {
        List<TreeNode> out = new ArrayList<>();
        for (UUID u : ctx.objects().keySet()) {
            TreeNode n = find(u);
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    /** 顶层节点（parent 为仓库根对象 uuid）。 */
    public List<TreeNode> roots() {
        UUID rootUuid = ctx.vault().rootObjectUuid();
        String root = rootUuid == null ? null : rootUuid.toString();
        List<TreeNode> out = new ArrayList<>();
        for (TreeNode n : nodes()) {
            if (root != null && root.equals(n.parentRef())) {
                out.add(n);
            }
        }
        return out;
    }

    /** 判断某对象是否属于本树（供 find/nodes 复用）。 */
    protected boolean isOwned(JsonObject data) {
        if (data == null) {
            return false;
        }
        StoredNodeType t = StoredNodeType.fromTag(data.getString("type"));
        return belongsTo(t, data.getString("kind"));
    }
}
