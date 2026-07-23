package com.flora.ramet.idea.lexer;

import com.flora.ramet.idea.RametLanguage;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Ramet 模板语言的 PSI 元素类型。
 *
 * <p>用于 {@link com.intellij.psi.tree.TokenSet} 分组和高亮色映射。
 */
public final class RametElementType extends IElementType {

    public RametElementType(@NotNull @NonNls String debugName) {
        super(debugName, RametLanguage.INSTANCE);
    }
}
