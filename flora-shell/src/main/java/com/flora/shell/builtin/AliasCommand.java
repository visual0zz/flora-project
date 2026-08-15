package com.flora.shell.builtin;

import com.flora.shell.Command;
import com.flora.shell.CommandResult;
import com.flora.shell.CommandService;
import com.flora.shell.Invocation;
import com.flora.shell.spec.ArgSpec;
import com.flora.shell.spec.ParsedArgs;

import java.util.List;

/**
 * 内置 {@code alias} 指令：注册一个别名，之后该名字被调用时转发到目标命令。
 * <p>用法：{@code alias <name> <cmd> [args...]}。之后 {@code name x y} 等价于调用
 * {@code cmd args... x y}（别名注册时附带的参数在前，本次调用参数追加在后）。
 * 别名由 {@link CommandService} 在分派未命中真实命令时解析。</p>
 * <p>以低优先级注册（{@code -100}），用户可定义同名命令覆写。</p>
 */
public final class AliasCommand implements Command {

    private final CommandService commandService;

    /**
     * @param commandService 指令组件（用于写别名注册表）
     */
    public AliasCommand(CommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public String name() {
        return "alias";
    }

    @Override
    public String description() {
        return "注册命令别名，之后调用该名字会转发到目标命令";
    }

    @Override
    public List<ArgSpec> args() {
        return List.of(
                ArgSpec.builder()
                        .kind(ArgSpec.Kind.POSITIONAL)
                        .name("name")
                        .required(true)
                        .description("别名")
                        .build(),
                ArgSpec.builder()
                        .kind(ArgSpec.Kind.POSITIONAL)
                        .name("cmd")
                        .required(true)
                        .description("目标命令名")
                        .build(),
                ArgSpec.builder()
                        .kind(ArgSpec.Kind.POSITIONAL)
                        .name("args")
                        .variadic(true)
                        .type(ArgSpec.Type.STRING_LIST)
                        .description("附加在每次调用参数之前的目标参数")
                        .build());
    }

    @Override
    public int priority() {
        return -100;
    }

    @Override
    public CommandResult execute(Invocation ctx) {
        ParsedArgs args = ctx.args();
        String name = args.get("name");
        String cmd = args.get("cmd");
        List<String> prefix = args.getStringList("args");
        commandService.setAlias(name, cmd, prefix);
        ctx.log().info("alias: {} -> {}{}", name, cmd,
                prefix.isEmpty() ? "" : " " + String.join(" ", prefix));
        return CommandResult.success();
    }
}
