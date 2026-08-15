package com.flora.sanctum.app.command;

import com.flora.shell.Command;
import com.flora.shell.Invocation;
import com.flora.shell.CommandResult;
import com.flora.shell.spec.ArgSpec;
import com.flora.sanctum.model.Sanctum;

import java.nio.file.Path;
import java.util.List;

/**
 * {@code create} 命令：新建并解锁一个库。
 */
public final class CreateCommand implements Command {

    @Override
    public String name() {
        return "create";
    }

    @Override
    public String description() {
        return "新建一个密码库并解锁";
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
        try {
            Sanctum.createAndUnlock(root, pw);
        } finally {
            java.util.Arrays.fill(pw, (char) 0);
        }
        ctx.log().info("created " + root);
        return CommandResult.success();
    }
}
