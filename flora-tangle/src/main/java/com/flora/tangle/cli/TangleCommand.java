package com.flora.tangle.cli;

import com.flora.shell.Command;
import com.flora.shell.CommandResult;
import com.flora.shell.Invocation;
import com.flora.shell.spec.ArgSpec;
import com.flora.tangle.Obfuscator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * tangle 混淆命令：把一个 jar 混淆成另一个 jar。
 * <p>声明式定义参数（{@code in}/{@code out}/可重复 {@code keep}），
 * 经 {@code com.flora.shell} 框架解析与执行，实际混淆逻辑委托 {@link Obfuscator}。</p>
 */
public final class TangleCommand implements Command {

    @Override
    public String name() {
        return "tangle.obfuscate";
    }

    @Override
    public String description() {
        return "把一个 jar 混淆成另一个 jar";
    }

    @Override
    public List<ArgSpec> args() {
        return List.of(
                ArgSpec.builder()
                        .kind(ArgSpec.Kind.POSITIONAL)
                        .name("in")
                        .required(true)
                        .description("输入 jar")
                        .build(),
                ArgSpec.builder()
                        .kind(ArgSpec.Kind.POSITIONAL)
                        .name("out")
                        .required(true)
                        .description("输出 jar")
                        .build(),
                ArgSpec.builder()
                        .kind(ArgSpec.Kind.OPTION)
                        .name("keep")
                        .type(ArgSpec.Type.STRING_LIST)
                        .description("保留的类内部名前缀（可重复）")
                        .build());
    }

    @Override
    public CommandResult execute(Invocation ctx) throws Exception {
        String inPath = ctx.args().get("in");
        String outPath = ctx.args().get("out");
        Path in = Path.of(inPath);
        Path out = Path.of(outPath);

        Obfuscator obf = new Obfuscator();
        for (String prefix : ctx.args().getStringList("keep")) {
            obf.keepClassPrefix(prefix);
        }

        byte[] input = Files.readAllBytes(in);
        byte[] result = obf.obfuscate(input);
        Files.write(out, result);
        ctx.out().println("混淆完成: " + in + " -> " + out + " (" + result.length + " 字节)");
        return CommandResult.success();
    }
}
