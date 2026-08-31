package com.flora.sanctum.app.ui;

import com.flora.sanctum.core.model.ViewNodeType;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.border.EmptyBorder;

/**
 * 设置页中栏条目渲染器：纯文本（{@link SettingsModel.SettingsEntry#label()}）+ 内边距（无字符图标）。
 */
final class SettingsEntryRenderer extends DefaultListCellRenderer {
    @Override
    public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setIcon(null);
        setBorder(new EmptyBorder(6, 8, 6, 8));
        // 列表项已是自描述的 SettingsEntry，用其 label（record 默认 toString 是字段列表，不适合直接展示）
        if (value instanceof SettingsModel.SettingsEntry entry) {
            setText(entry.label());
        } else {
            setText(value == null ? "" : value.toString());
        }
        return this;
    }
}
