package com.flora.shell.help;

import com.flora.shell.Command;
import com.flora.shell.spec.ArgSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 帮助渲染器：把一组命令渲染为文本树 / 单命令帮助。
 * <p>按命令名点分路径构建子命令树，渲染全局帮助；或渲染单个命令的参数行。
 * 命令类是帮助的唯一事实来源，本类只做聚合与排版。</p>
 */
public final class HelpRenderer {

    /**
     * 渲染全局帮助：按点分名构建命令树。
     *
     * @param commands 全部已注册命令
     * @return 全局帮助文本
     */
    public String renderGlobal(List<Command> commands) {
        TreeNode root = new TreeNode("");
        for (Command c : commands) {
            String[] parts = c.name().split("\\.");
            TreeNode node = root;
            for (String part : parts) {
                node = node.children.computeIfAbsent(part, TreeNode::new);
            }
            node.command = c;
        }
        StringBuilder sb = new StringBuilder("可用命令：\n");
        renderTree(root, "", sb);
        return sb.toString();
    }

    /**
     * 渲染单个命令的帮助（用法 + 参数行）。
     *
     * @param c 命令
     * @return 单命令帮助文本
     */
    public String renderCommand(Command c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.name()).append(" - ").append(c.description()).append('\n');
        sb.append("用法: ").append(usage(c)).append('\n');
        List<ArgSpec> args = c.args();
        if (!args.isEmpty()) {
            sb.append("\n参数:\n");
            for (ArgSpec a : args) {
                sb.append("  ").append(a.helpLine()).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 由声明自动生成一行用法；若命令自定义了 {@code usage()} 则用其覆盖。
     *
     * @param c 命令
     * @return 用法字符串
     */
    public String usage(Command c) {
        String custom = c.usage();
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        StringBuilder sb = new StringBuilder(c.name());
        for (ArgSpec a : c.args()) {
            if (a.kind() == ArgSpec.Kind.POSITIONAL) {
                if (!a.required()) {
                    sb.append(" [<").append(a.name()).append('>');
                } else {
                    sb.append(" <").append(a.name()).append('>');
                }
                if (a.variadic()) {
                    sb.append("...");
                }
                if (!a.required()) {
                    sb.append(']');
                }
            } else if (a.required()) {
                sb.append(" --").append(a.name());
            }
        }
        return sb.toString();
    }

    private void renderTree(TreeNode node, String prefix, StringBuilder sb) {
        List<Map.Entry<String, TreeNode>> entries = new ArrayList<>(node.children.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, TreeNode> e = entries.get(i);
            boolean last = i == entries.size() - 1;
            TreeNode child = e.getValue();
            String connector = last ? "└── " : "├── ";
            String desc = child.command != null ? "  " + child.command.description() : "";
            sb.append(prefix).append(connector).append(e.getKey()).append(desc).append('\n');
            String childPrefix = prefix + (last ? "    " : "│   ");
            renderTree(child, childPrefix, sb);
        }
    }

    /** 子命令树节点。 */
    private static final class TreeNode {
        final String name;
        final Map<String, TreeNode> children = new LinkedHashMap<>();
        Command command;

        TreeNode(String name) {
            this.name = name;
        }
    }
}
