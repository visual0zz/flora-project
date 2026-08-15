package com.flora.sanctum.app.command;

import com.flora.shell.Command;
import com.flora.shell.CommandResult;
import com.flora.shell.Invocation;
import com.flora.shell.spec.ArgSpec;
import com.flora.sanctum.model.Sanctum;

import java.nio.file.Path;
import java.util.List;

/**
 * {@code export} 命令：导出加密归档（密文 zip，见设计 03"备份"）。
 */
public final class ExportCommand implements Command {

    @Override
    public String name() {
        return "export";
    }

    @Override
    public String description() {
        return "导出加密归档为 zip";
    }

    @Override
    public List<ArgSpec> args() {
        return List.of(
                ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("path")
                        .required(true).description("库目录路径").build(),
                ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("out")
                        .required(true).description("输出 zip 路径").build());
    }

    @Override
    public CommandResult execute(Invocation ctx) throws Exception {
        Path root = Path.of(ctx.args().get("path").asString());
        Path out = Path.of(ctx.args().get("out").asString());
        char[] pw = MainUtil.readPassword("master password");
        try (Sanctum s = Sanctum.open(root)) {
            s.unlock(pw);
            s.exportArchive(out);
        } finally {
            java.util.Arrays.fill(pw, (char) 0);
        }
        ctx.log().info("exported to " + out);
        return CommandResult.success();
    }
}
