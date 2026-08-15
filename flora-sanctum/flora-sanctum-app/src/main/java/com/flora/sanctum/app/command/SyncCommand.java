package com.flora.sanctum.app.command;

import com.flora.shell.Command;
import com.flora.shell.CommandResult;
import com.flora.shell.Invocation;
import com.flora.shell.spec.ArgSpec;
import com.flora.sanctum.model.Sanctum;
import com.flora.sanctum.sync.SyncService;

import java.nio.file.Path;
import java.util.List;

/**
 * {@code sync} 命令：与远端同步（完全托管仓库）。
 */
public final class SyncCommand implements Command {

    @Override
    public String name() {
        return "sync";
    }

    @Override
    public String description() {
        return "与远端同步（完全托管仓库）";
    }

    @Override
    public List<ArgSpec> args() {
        return List.of(ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("path")
                .required(true).description("库目录路径").build());
    }

    @Override
    public CommandResult execute(Invocation ctx) throws Exception {
        Path root = Path.of(ctx.args().get("path").asString());
        try (Sanctum s = MainUtil.openUnlocked(root)) {
            SyncService sync = new SyncService(root);
            if (!sync.isFullyManaged()) {
                ctx.log().error("not fully managed, skip sync");
                return CommandResult.commandError();
            }
            s.close();
            sync.sync();
            ctx.log().info("synced " + root);
        }
        return CommandResult.success();
    }
}
