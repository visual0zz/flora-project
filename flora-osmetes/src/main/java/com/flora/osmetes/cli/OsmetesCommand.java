package com.flora.osmetes.cli;

import com.flora.osmetes.CheckIssue;
import com.flora.osmetes.Osmetes;
import com.flora.osmetes.Severity;
import com.flora.shell.Command;
import com.flora.shell.CommandResult;
import com.flora.shell.Invocation;
import com.flora.shell.spec.ArgSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * osmetes 检查命令：扫描指定根路径并打印报告。
 * <p>声明式定义参数（{@code sourceRoot}），经 {@code com.flora.shell} 框架解析与执行。
 * 输出经 {@code Invocation.out()} 写入，可同时到达批量 stdout 与未来挂载的输出汇。</p>
 */
public final class OsmetesCommand implements Command {

    /** CLI 输出前缀，用于区分 osmetes 自身的提示与检查结果。 */
    public static final String CLI_PREFIX = "[flora-osmetes]";

    @Override
    public String name() {
        return "osmetes.check";
    }

    @Override
    public String description() {
        return "扫描源码目录并报告编码、密钥等问题";
    }

    @Override
    public String allowedSourcePattern() {
        return ".*";
    }

    @Override
    public List<ArgSpec> args() {
        return List.of(ArgSpec.builder()
                .kind(ArgSpec.Kind.POSITIONAL)
                .name("sourceRoot")
                .required(true)
                .description("要扫描的根路径")
                .build());
    }

    @Override
    public CommandResult execute(Invocation ctx) throws Exception {
        String rootArg = ctx.args().get("sourceRoot");
        Path root = Paths.get(rootArg).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            ctx.out().println(CLI_PREFIX + " 跳过（目录不存在）: " + root);
            return CommandResult.success();
        }
        List<CheckIssue> issues = Osmetes.run(root, Osmetes.discoverChecks());
        print(ctx, issues);
        long errors = Osmetes.countErrors(issues);
        if (errors > 0) {
            ctx.out().error("osmetes 检查失败，共 " + errors + " 个错误、"
                    + Osmetes.countWarnings(issues) + " 个警告");
            return CommandResult.failure();
        }
        return CommandResult.success();
    }

    private static void print(Invocation ctx, List<CheckIssue> issues) {
        long errors = Osmetes.countErrors(issues);
        long warnings = Osmetes.countWarnings(issues);
        if (issues.isEmpty()) {
            ctx.out().println(CLI_PREFIX + " 检查通过");
            return;
        }
        ctx.out().println(CLI_PREFIX + " 共发现 " + errors + " 个错误、" + warnings + " 个警告：");
        for (CheckIssue issue : issues) {
            String marker = issue.severity() == Severity.ERROR ? "ERROR" : "WARN ";
            ctx.out().println(String.format("  [%s] %s [%s] %s%n",
                    marker, issue.location(), issue.check(), issue.message()));
        }
    }
}
