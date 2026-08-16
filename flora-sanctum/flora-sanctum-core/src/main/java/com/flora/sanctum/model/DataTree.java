package com.flora.sanctum.model;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 数据树容器（Sanctum = 元数据 + 配置数据 + List&lt;数据树&gt;）。
 * <p>
 * 每棵树对应一个根概念 tag（DATA/ICON/SSH_KEY/REMOTE），承载该概念根下
 * 的对象节点；节点负责新建/编辑/删除等操作（见设计 05"数据结构树化"）。
 */
public abstract class DataTree {

    private final RootTag tag;
    private final TreeContext ctx;

    protected DataTree(RootTag tag, TreeContext ctx) {
        this.tag = tag;
        this.ctx = ctx;
    }

    public RootTag tag() {
        return tag;
    }

    public TreeContext context() {
        return ctx;
    }

    /** 该树的类型集合（子类声明归属类型）。 */
    protected abstract boolean belongsTo(String type, String kind);

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

    /** 顶层节点（parent 为本树根概念 tag）。 */
    public List<TreeNode> roots() {
        List<TreeNode> out = new ArrayList<>();
        for (TreeNode n : nodes()) {
            if (tag.tag().equals(n.parent())) {
                out.add(n);
            }
        }
        return out;
    }

    /** 判断某对象是否属于本树（供 find/nodes 复用）。 */
    protected boolean isOwned(JsonObject data) {
        return data != null && belongsTo(data.getString("type"), data.getString("kind"));
    }
}
