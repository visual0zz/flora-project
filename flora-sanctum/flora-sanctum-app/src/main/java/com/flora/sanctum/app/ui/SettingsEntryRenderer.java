package com.flora.sanctum.app.ui;

import com.flora.sanctum.core.model.Ref;

import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JList;
import javax.swing.border.EmptyBorder;
import java.util.function.BiFunction;

/**
 * 设置页中栏条目渲染器：文本（{@link SettingsModel.SettingsEntry#label()}）+ 内边距；
 * 图标类条目（{@code ICON}）额外在左侧渲染一个小号缩略图，便于在列表里直接辨识。
 */
final class SettingsEntryRenderer extends DefaultListCellRenderer {
    /** 按图标引用与尺寸解析 Icon（注入 {@link SanctumGui#iconById}）。 */
    private final BiFunction<Ref, Integer, Icon> iconResolver;

    SettingsEntryRenderer(BiFunction<Ref, Integer, Icon> iconResolver) {
        this.iconResolver = iconResolver;
    }

    @Override
    public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setIcon(null);
        setBorder(new EmptyBorder(6, 8, 6, 8));
        if (value instanceof SettingsModel.SettingsEntry entry) {
            setText(entry.label());
            // 图标条目：在文本左侧附一个小号缩略图（与全局图标尺寸风格一致的 24px）
            if (entry instanceof SettingsModel.ObjectEntry oe
                    && oe.kind() == SettingsModel.SettingsCategory.Kind.ICON) {
                Icon ic = iconResolver.apply(Ref.fromLegacyId(oe.id()), 24);
                if (ic != null) {
                    setIcon(ic);
                }
            }
        } else {
            setText(value == null ? "" : value.toString());
        }
        return this;
    }
}
