package com.flora.shell.builtin;

import com.flora.shell.*;
import com.flora.shell.help.HelpRenderer;
import com.flora.shell.spec.ArgSpec;
import com.flora.shell.spec.ParsedArgs;

import java.util.List;

/**
 * 内置 {@code help} 指令：遍历组件注册表，渲染全局命令树或单命令帮助。
 * <p>以低优先级注册（{@code -100}），用户可定义同名命令覆写。</p>
 */
public final class HelpCommand implements Command {

    private final CommandService component;
    private final HelpRenderer renderer;

    /**
     * @param component 指令组件（用于读取注册表）
     * @param renderer  帮助渲染器
     */
    public HelpCommand(CommandService component, HelpRenderer renderer) {
        this.component = component;
        this.renderer = renderer;
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
        ParsedArgs args = ctx.args();
        String cmd = args.get("cmd");
        if (cmd == null || cmd.isBlank()) {
            ctx.out().println(renderer.renderGlobal(component.commands()));
        } else {
            Command c = component.find(cmd);
            if (c == null) {
                ctx.out().error("未知命令: " + cmd);
                return CommandResult.failure();
            }
            ctx.out().println(renderer.renderCommand(c));
        }
        return CommandResult.success();
    }
}
