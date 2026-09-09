package com.flora.sanctum.core.model;

import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.FieldNode;
import com.flora.sanctum.core.model.tree.ObjectTree;
import com.flora.sanctum.core.model.tree.TreeNode;

import java.util.ArrayList;
import java.util.List;

/**
 * TOTP 虚拟区段数据源（与 {@link TrashView} 同级的只读聚合视图）。
 * <p>聚合全部未删除条目下 {@code kind:"totp"} 的字段；扫描逻辑属数据层，UI 仅消费结果。</p>
 */
public final class TotpView {

    private final List<FieldNode> fields;

    TotpView(List<FieldNode> fields) {
        this.fields = fields;
    }

    /** 全部 kind:"totp" 字段（跨所有未删除条目）。 */
    public List<FieldNode> fields() {
        return fields;
    }

    /** 基于对象树构建 TOTP 视图。 */
    public static TotpView of(ObjectTree tree) {
        List<FieldNode> out = new ArrayList<>();
        for (TreeNode n : tree.nodes()) {
            if (n instanceof EntryNode e && !e.deleted()) {
                for (FieldNode f : e.fields()) {
                    if ("totp".equals(f.kind())) {
                        out.add(f);
                    }
                }
            }
        }
        return new TotpView(out);
    }
}
