package com.flora.ramet.idea;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Ramet 模板文件类型（.ftl 扩展名）。
 */
public final class RametFileType extends LanguageFileType {

    public static final RametFileType INSTANCE = new RametFileType();

    private RametFileType() {
        super(RametLanguage.INSTANCE);
    }

    @Override
    public @NonNls @NotNull String getName() {
        return "Ramet";
    }

    @Override
    public @NotNull String getDescription() {
        return "Ramet 模板文件";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "ramet";
    }

    @Override
    public Icon getIcon() {
        return RametIcons.FILE;
    }
}
