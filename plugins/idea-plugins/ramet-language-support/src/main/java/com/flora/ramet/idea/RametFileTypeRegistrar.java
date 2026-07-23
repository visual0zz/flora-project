package com.flora.ramet.idea;

import com.intellij.ide.highlighter.FileTypeRegistrar;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

/**
 * Ramet 文件类型注册器——用于向 IDE 注册 .ramet 文件关联。
 */
public final class RametFileTypeRegistrar implements FileTypeRegistrar {

    @Override
    public void initFileType(@NotNull FileType fileType) {
        // FileType 已通过 getDefaultExtension() 自动关联扩展名
    }
}
