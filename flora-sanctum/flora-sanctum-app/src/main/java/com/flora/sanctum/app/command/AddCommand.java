package com.flora.sanctum.app.command;

import com.flora.shell.Command;
import com.flora.shell.CommandResult;
import com.flora.shell.Invocation;
import com.flora.shell.spec.ArgSpec;
import com.flora.sanctum.model.Sanctum;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code add} 命令：在指定库新建一个条目（自动解锁）。
 */
public final class AddCommand implements Command {

    @Override
    public String name() {
        return "add";
    }

    @Override
    public String description() {
        return "在库中新建一个条目";
    }

    @Override
    public List<ArgSpec> args() {
        return List.of(
                ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("path")
                        .required(true).description("库目录路径").build(),
                ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("name")
                        .required(true).description("条目名").build());
    }

    @Override
    public CommandResult execute(Invocation ctx) throws Exception {
        Path root = Path.of(ctx.args().get("path").asString());
        String name = ctx.args().get("name").asString();
        try (Sanctum s = MainUtil.openUnlocked(root)) {
            UUID uuid = s.createEntry(null, name, Map.of());
            ctx.log().info("added " + name + " -> " + uuid);}
        return CommandResult.success();
    }
}
