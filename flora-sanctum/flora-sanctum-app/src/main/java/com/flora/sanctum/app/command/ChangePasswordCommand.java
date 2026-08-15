package com.flora.sanctum.app.command;

import com.flora.shell.Command;
import com.flora.shell.CommandResult;
import com.flora.shell.Invocation;
import com.flora.shell.spec.ArgSpec;
import com.flora.sanctum.model.Sanctum;

import java.nio.file.Path;
import java.util.List;

/**
 * {@code change-password} 命令：换主密码（可升级 KDF 参数）。
 */
public final class ChangePasswordCommand implements Command {

    @Override
    public String name() {
        return "change-password";
    }

    @Override
    public String description() {
        return "换主密码（可升级 Argon2id 参数）";
    }

    @Override
    public List<ArgSpec> args() {
        return List.of(
                ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("path")
                        .required(true).description("库目录路径").build(),
                ArgSpec.builder().kind(ArgSpec.Kind.OPTION).name("memory")
                        .description("Argon2id memory KiB（默认 262144）").build(),
                ArgSpec.builder().kind(ArgSpec.Kind.OPTION).name("iterations")
                        .description("Argon2id 迭代次数（默认 3）").build(),
                ArgSpec.builder().kind(ArgSpec.Kind.OPTION).name("parallelism")
                        .description("Argon2id 并行度（默认 4）").build());
    }

    @Override
    public CommandResult execute(Invocation ctx) throws Exception {
        Path root = Path.of(ctx.args().get("path").asString());
        int memory = ctx.args().getInt("memory") == null ? 262144 : ctx.args().getInt("memory");
        int iterations = ctx.args().getInt("iterations") == null ? 3 : ctx.args().getInt("iterations");
        int parallelism = ctx.args().getInt("parallelism") == null ? 4 : ctx.args().getInt("parallelism");
        char[] oldPw = MainUtil.readPassword("current master password");
        char[] newPw = MainUtil.readPassword("new master password");
        try (Sanctum s = Sanctum.open(root)) {
            s.unlock(oldPw);
            s.changeMasterPassword(newPw, memory, iterations, parallelism);
            s.close();
        } finally {
            java.util.Arrays.fill(oldPw, (char) 0);
            java.util.Arrays.fill(newPw, (char) 0);
        }
        ctx.log().info("password changed");
        return CommandResult.success();
    }
}
