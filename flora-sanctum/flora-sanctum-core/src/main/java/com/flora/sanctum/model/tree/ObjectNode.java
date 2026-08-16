package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import java.util.UUID;

/**
 * 普通对象树节点抽象（组/条目/字段的统一基类）。
 */
public abstract class ObjectNode extends TreeNode {

    ObjectNode(UUID uuid, ObjectTree tree) {
        super(uuid, tree);
    }

    @Override
    public ObjectTree tree() {
        return (ObjectTree) super.tree();
    }
}
