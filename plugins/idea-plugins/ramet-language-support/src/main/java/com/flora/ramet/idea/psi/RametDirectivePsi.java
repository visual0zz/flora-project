package com.flora.ramet.idea.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 指令节点（{@code <#include ...>}、{@code <#if ...>} 等）的 PSI 实现。
 *
 * <p>仅为 PSI 树中的指令占位节点，不提供引用跳转。
 */
public class RametDirectivePsi extends ASTWrapperPsiElement {

    public RametDirectivePsi(ASTNode node) {
        super(node);
    }
}
