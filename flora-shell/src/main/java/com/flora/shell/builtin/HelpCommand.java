package com.flora.shell.builtin;

import com.flora.shell.*;
import com.flora.shell.spec.ArgSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置 {@code help} 指令：遍历组件注册表，渲染全局命令树或单命令帮助。
 * <p>以低优先级注册（{@code -100}），用户可定义同名命令覆写。</p>
 */
public final class HelpCommand implements Command {

    private final CommandService commandService;

    /**
     * @param commandService 指令组件（用于读取注册表）
     */
    public HelpCommand(CommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "显示可用命令及用法";
    }

    @Override
    public List<ArgSpec> args() {
        return List.of(ArgSpec.builder()
                .kind(ArgSpec.Kind.POSITIONAL)
                .name("cmd")
                .description("可选：要查看的具体命令名")
                .build());
    }

    @Override
    public int priority() {
        return -100;
    }

    @Override
    public CommandResult execute(Invocation ctx) {
        String cmd = ctx.args().get("cmd") == null ? null : ctx.args().get("cmd").asString();
        if (cmd == null || cmd.isBlank()) {
            ctx.log().info(HelpRenderer.renderGlobal(commandService.commands()));
            return CommandResult.success();
        }
        Command c = commandService.find(cmd);
        if (c == null) {
            ctx.log().error("未知命令: {}", cmd);
            return CommandResult.commandError();
        }
        ctx.log().info(HelpRenderer.renderCommand(c));
        return CommandResult.success();
    }

    /**
     * 帮助渲染器：把一组命令渲染为文本树 / 单命令帮助。
     * <p>按命令名点分路径构建子命令树，渲染全局帮助；或渲染单个命令的参数行。
     * 命令类是帮助的唯一事实来源，本类只做聚合与排版。所有方法均为无状态的静态方法。</p>
     */
    public static final class HelpRenderer {

        private HelpRenderer() {
        }

        /**
         * 渲染全局帮助：按点分名构建命令树。
         *
         * @param commands 全部已注册命令
         * @return 全局帮助文本
         */
        public static String renderGlobal(List<Command> commands) {
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
        public static String renderCommand(Command c) {
            StringBuilder sb = new StringBuilder();
            sb.append(c.name()).append(" - ").append(c.description()).append('\n');
            sb.append("用法: ").append(c.usage()).append('\n');
            List<ArgSpec> args = c.args();
            if (!args.isEmpty()) {
                sb.append("\n参数:\n");
                for (ArgSpec a : args) {
                    sb.append("  ").append(a.helpLine()).append('\n');
                }
            }
            return sb.toString();
        }

        private static void renderTree(TreeNode node, String prefix, StringBuilder sb) {
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
}
