package com.flora.sanctum.app.ui;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * 设置页左栏渲染器：区段显示名（全局设置/仓库设置/图标/SSH 密钥/远程），无字符图标。
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
            // 区段节点：显示名已随 SettingsCategory 一并携带
            if (uo instanceof SettingsModel.SettingsCategory cat) {
                return cat.label();
            }
            // 根节点："设置"
            if (uo instanceof String s) {
                return s;
            }
        }
        return "?";
    }
}
