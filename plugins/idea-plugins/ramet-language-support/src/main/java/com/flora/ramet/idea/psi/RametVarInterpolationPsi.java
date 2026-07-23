package com.flora.ramet.idea.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * {@code ${...}} 插值节点的 PSI 实现。
 *
 * <p>仅为 PSI 树中的变量插值占位节点，不提供引用跳转。
 */
public class RametVarInterpolationPsi extends ASTWrapperPsiElement {

    public RametVarInterpolationPsi(ASTNode node) {
        super(node);
    }
}
