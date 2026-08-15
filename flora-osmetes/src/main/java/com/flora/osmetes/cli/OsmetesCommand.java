package com.flora.osmetes.cli;

import com.flora.osmetes.CheckIssue;
import com.flora.osmetes.Osmetes;
import com.flora.osmetes.Severity;
import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonString;
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
 * 输出经 {@link CommandResult} 返回，由框架扇出到批量 stdout 与未来挂载的输出汇。</p>
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
        String rootArg = ctx.args().get("sourceRoot").asString();
        Path root = Paths.get(rootArg).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            ctx.log().info("{} 跳过（目录不存在）: {}", CLI_PREFIX, root);
            return CommandResult.success();
        }
        List<CheckIssue> issues = Osmetes.run(root, Osmetes.discoverChecks());
        String report = render(issues);
        long errors = Osmetes.countErrors(issues);
        if (errors > 0) {
            ctx.log().error(report + "\n" + CLI_PREFIX + " 检查失败，共 " + errors + " 个错误、"
                    + Osmetes.countWarnings(issues) + " 个警告");
            return CommandResult.commandError();
        }
        ctx.log().info(report);
        return CommandResult.data(toJson(issues));
    }

    /** 把检查问题列表转为 JSON 数组（供机器可读消费）。 */
    private static JsonArray toJson(List<CheckIssue> issues) {
        JsonArray array = new JsonArray();
        for (CheckIssue issue : issues) {
            array.add(new JsonObject()
                    .put("file", new JsonString(issue.relativeFile()))
                    .put("line", issue.line())
                    .put("column", issue.column())
                    .put("check", new JsonString(issue.check()))
                    .put("severity", new JsonString(issue.severity().name()))
                    .put("message", new JsonString(issue.message())));
        }
        return array;
    }

    /** 渲染检查报告文本（含"检查通过"或问题明细）。 */
    private static String render(List<CheckIssue> issues) {
        long errors = Osmetes.countErrors(issues);
        long warnings = Osmetes.countWarnings(issues);
        if (issues.isEmpty()) {
            return CLI_PREFIX + " 检查通过";
        }
        StringBuilder sb = new StringBuilder(CLI_PREFIX + " 共发现 " + errors + " 个错误、"
                + warnings + " 个警告：");
        for (CheckIssue issue : issues) {
            String marker = issue.severity() == Severity.ERROR ? "ERROR" : "WARN ";
            sb.append('\n').append(String.format("  [%s] %s [%s] %s",
                    marker, issue.location(), issue.check(), issue.message()));
        }
        return sb.toString();
    }
}
