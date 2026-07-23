package com.flora.ramet.idea.parser;

import com.flora.ramet.idea.RametLanguage;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IElementType;

/**
 * Ramet 复合节点类型。
 */
public final class RametNodeType extends IElementType {

    public RametNodeType(String debugName) {
        super(debugName, RametLanguage.INSTANCE);
    }
}
