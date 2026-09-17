package com.flora.sanctum.app.ui;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;
import com.flora.sanctum.core.model.Ref;

import javax.swing.DefaultListCellRenderer;
import java.util.UUID;
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

    private static final Logger LOG = LoggerFactory.getLogger(SettingsEntryRenderer.class);

    /** 单实例渲染器在持续重绘中会反复触发同一异常，限制落盘次数避免日志刷屏。 */
    private static final int MAX_RENDER_ERROR_LOG = 10;
    private int renderErrorCount = 0;

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
                // 图标缩略图解析可能抛异常（如内置图标 id 误用 UUID 解析）；该类异常发生在
                // JList 绘制阶段、会被 Swing 静默吞掉，故在此兜底记录到日志，且不落任何条目名/密钥名。
                try {
                    String id = oe.id();
                    Ref iconRef = id != null && id.startsWith("builtin:")
                            ? Ref.builtinIcon(id.substring("builtin:".length()))
                            : Ref.nodeIcon(UUID.fromString(id));
                    Icon ic = iconResolver.apply(iconRef, 24);
                    if (ic != null) {
                        setIcon(ic);
                    }
                } catch (RuntimeException ex) {
                    if (renderErrorCount < MAX_RENDER_ERROR_LOG) {
                        renderErrorCount++;
                        LOG.error("Failed to render settings entry thumbnail (index={}, kind={})",
                                index, oe.kind(), ex);
                    }
                }
            }
        } else {
            setText(value == null ? "" : value.toString());
        }
        return this;
    }
}
