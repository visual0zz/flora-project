package com.flora.sanctum.app.command;

import com.flora.shell.Command;
import com.flora.shell.CommandResult;
import com.flora.shell.Invocation;
import com.flora.shell.spec.ArgSpec;
import com.flora.sanctum.model.Json;
import com.flora.sanctum.model.Sanctum;

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
        char[] pw = MainUtil.readPassword("master password");
        try (Sanctum s = Sanctum.open(root)) {
            s.unlock(pw);
            StringBuilder sb = new StringBuilder();
            for (UUID u : s.store().list()) {
                Json.Node n = s.getEntry(u);
                sb.append(u).append(' ').append(n == null ? "?" : n.str("type") + "/" + n.str("name")).append('\n');
            }
            ctx.log().info(sb.toString().stripTrailing());
        } finally {
            java.util.Arrays.fill(pw, (char) 0);
        }
        return CommandResult.success();
    }
}
