package com.flora.ramet.cli;

import com.flora.ramet.Ramet;
import com.flora.shell.Command;
import com.flora.shell.CommandResult;
import com.flora.shell.Invocation;
import com.flora.shell.spec.ArgSpec;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * ramet 代码生成命令：递归扫描模板目录并生成源码。
 * <p>声明式定义参数（{@code templatesDir}/{@code outputDir}/{@code dryRun}），
 * 经 {@code com.flora.shell} 框架解析与执行，实际生成逻辑委托 {@link Ramet#run}。</p>
 */
public final class RametCommand implements Command {

    @Override
    public String name() {
        return "ramet.gen";
    }

    @Override
    public String description() {
        return "扫描模板目录并按元数据生成源码";
    }

    @Override
    public List<ArgSpec> args() {
        return List.of(
                ArgSpec.builder()
                        .kind(ArgSpec.Kind.POSITIONAL)
                        .name("templatesDir")
                        .required(true)
                        .description("模板目录")
                        .build(),
                ArgSpec.builder()
                        .kind(ArgSpec.Kind.POSITIONAL)
                        .name("outputDir")
                        .required(true)
                        .description("输出目录")
                        .build(),
                ArgSpec.builder()
                        .kind(ArgSpec.Kind.OPTION)
                        .name("dry-run")
                        .type(ArgSpec.Type.BOOLEAN)
                        .description("只打印输出路径，不写文件")
                        .defaultValue(false)
                        .build());
    }

    @Override
    public CommandResult execute(Invocation ctx) throws Exception {
        String tpl = ctx.args().get("templatesDir");
        String out = ctx.args().get("outputDir");
        Path templatesDir = Paths.get(tpl).toAbsolutePath();
        Path outputDir = Paths.get(out).toAbsolutePath();
        boolean dryRun = ctx.args().getBoolean("dry-run");
        Ramet.run(templatesDir, outputDir, dryRun);
        return CommandResult.success();
    }
}
