package com.flora.sanctum.app.command;

import com.flora.shell.Command;
import com.flora.shell.CommandResult;
import com.flora.shell.Invocation;
import com.flora.shell.spec.ArgSpec;
import com.flora.sanctum.model.Sanctum;
import com.flora.root.codec.json.model.JsonObject;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * {@code list} 命令：列出库中全部对象（自动解锁）。
 */
public final class ListCommand implements Command {

    @Override
    public String name() {
        return "list";
    }

    @Override
    public String description() {
        return "列出库中对象";
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
            StringBuilder sb = new StringBuilder();
            for (UUID u : s.listObjectUuids()) {
                JsonObject n = s.getEntry(u);
                sb.append(u).append(' ').append(n == null ? "?" : n.getString("type") + "/" + n.getString("name")).append('\n');
            }
            ctx.log().info(sb.toString().stripTrailing());
        }
        return CommandResult.success();
    }
}
