package com.flora.sanctum.app.ui;

import com.flora.sanctum.model.ViewNodeType;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * 设置页左栏渲染器：root 显示名（设置/图标/SSH 密钥/远程），无字符图标。从 {@code SanctumGui}
 * 抽出为独立类，因其仅依赖 {@link ViewNodeType} 与节点 userObject，不依赖实例状态。
 */
final class SettingsTreeRenderer extends DefaultTreeCellRenderer {
    @Override
    public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                           boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        setIcon(null);
        setText(rootName(value));
        return this;
    }

    private static String rootName(Object value) {
        if (value instanceof DefaultMutableTreeNode node) {
            Object uo = node.getUserObject();
            if (uo instanceof ViewNodeType tag) {
                return switch (tag) {
                    case ICON -> "图标";
                    case SSH_KEY -> "SSH 密钥";
                    case REMOTE -> "远程";
                    case PASSWORD -> "密码库";
                    default -> "设置";
                };
            }
            if (uo == ViewNodeType.SETTINGS) {
                return "设置";
            }
        }
        return "?";
    }
}
