package com.flora.ramet.idea.psi;

import com.flora.ramet.idea.RametFileType;
import com.flora.ramet.idea.RametLanguage;
import com.flora.ramet.idea.parser.RametTypes;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Ramet 模板文件的 PSI 根节点。
 */
public class RametFile extends PsiFileBase {

    public RametFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, RametLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return RametFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "RametFile:" + getName();
    }
}
