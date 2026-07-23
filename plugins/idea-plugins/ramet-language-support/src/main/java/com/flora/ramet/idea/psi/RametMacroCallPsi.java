package com.flora.ramet.idea.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 宏调用节点（{@code <@macroName ...>}）的 PSI 实现。
 *
 * <p>仅为 PSI 树中的宏调用占位节点，不提供引用跳转。
 */
public class RametMacroCallPsi extends ASTWrapperPsiElement {

    public RametMacroCallPsi(ASTNode node) {
        super(node);
    }
}
