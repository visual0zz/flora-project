package com.flora.tangle;

import com.flora.shell.CommandService;
import com.flora.shell.InputEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令行入口：把一个 jar 混淆成另一个 jar。
 *
 * <p>用法：<br>
 * {@code java -jar flora-tangle.jar <输入.jar> <输出.jar> [--keep <类前缀> ...]}
 *
 * <p>{@code --keep} 后可跟若干类内部名前缀（如 {@code com/foo/})，这些类不会被重命名，
 * 通常用于保留程序入口或需要被外部反射调用的类。
 *
 * <p>实际混淆逻辑见 {@link com.flora.tangle.cli.TangleCommand}（经 {@code com.flora.shell}
 * 命令框架解析参数与执行），本类仅提供入口壳。
 */
public final class Tangle {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("用法: Tangle <输入.jar> <输出.jar> [--keep <类前缀> ...]");
            System.exit(2);
            return;
        }
        CommandService commandService = new CommandService();
        commandService.register(new com.flora.tangle.cli.TangleCommand());
        // 本工具以命令名作为首参，保持原有 "Tangle <in> <out> ..." 的命令行契约
        List<String> cliArgs = new ArrayList<>(args.length + 1);
        cliArgs.add("tangle.obfuscate");
        cliArgs.addAll(List.of(args));
        int exitCode = commandService.submit(InputEvent.ofCliArgs(cliArgs)).exitCode();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
