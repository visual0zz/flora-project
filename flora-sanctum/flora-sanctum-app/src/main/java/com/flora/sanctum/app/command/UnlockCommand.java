package com.flora.sanctum.app.command;

import com.flora.shell.Command;
import com.flora.shell.CommandResult;
import com.flora.shell.Invocation;
import com.flora.shell.spec.ArgSpec;
import com.flora.sanctum.model.Sanctum;

import java.nio.file.Path;
import java.util.List;

/**
 * {@code unlock} 命令：解锁一个库（验证主密码）。
 */
public final class UnlockCommand implements Command {

    @Override
    public String name() {
        return "unlock";
    }

    @Override
    public String description() {
        return "解锁一个密码库";
    }

    @Override
    public List<ArgSpec> args() {
        return List.of(ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("path")
                .required(true).description("库目录路径").build());
    }

    @Override
    public CommandResult execute(Invocation ctx) throws Exception {
        Path root = Path.of(ctx.args().get("path").asString());
        char[] pw = MainUtil.readPassword("master password");
        try (Sanctum s = Sanctum.open(root)) {
            s.unlock(pw);
            ctx.log().info("unlocked " + root + ", objects=" + s.objectCount());
        } finally {
            java.util.Arrays.fill(pw, (char) 0);
        }
        return CommandResult.success();
    }
}
