package com.flora.sanctum.app.command;

import com.flora.shell.Command;
import com.flora.shell.CommandResult;
import com.flora.shell.Invocation;
import com.flora.shell.spec.ArgSpec;
import com.flora.sanctum.model.Sanctum;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.JsonUtil;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * {@code get} 命令：读取一个对象（自动解锁）。
 */
public final class GetCommand implements Command {

    @Override
    public String name() {
        return "get";
    }

    @Override
    public String description() {
        return "读取一个对象";
    }

    @Override
    public List<ArgSpec> args() {
        return List.of(
                ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("path")
                        .required(true).description("库目录路径").build(),
                ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("uuid")
                        .required(true).description("对象 UUID").build());
    }

    @Override
    public CommandResult execute(Invocation ctx) throws Exception {
        Path root = Path.of(ctx.args().get("path").asString());
        UUID uuid = UUID.fromString(ctx.args().get("uuid").asString());
        try (Sanctum s = MainUtil.openUnlocked(root)) {
            com.flora.sanctum.model.tree.TreeNode n = s.findNode(uuid);
            ctx.log().info(n == null ? "not found" : JsonUtil.toJsonString(n.data()));}
        return CommandResult.success();
    }
}
