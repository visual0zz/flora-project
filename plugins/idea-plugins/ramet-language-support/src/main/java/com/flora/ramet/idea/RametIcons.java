package com.flora.ramet.idea;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * Ramet 插件用到的图标。
 */
public final class RametIcons {

    private RametIcons() {
    }

    /** .ftl 文件图标（使用 Text 文件图标作为占位）。 */
    public static final Icon FILE = IconLoader.getIcon("/icons/ramet.svg", RametIcons.class);
}
