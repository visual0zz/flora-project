package com.flora.sanctum.app;

import com.flora.sanctum.model.Json;
import com.flora.sanctum.model.Sanctum;

import java.io.Console;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * flora-sanctum 应用入口（单一可执行 jar）。
 * <p>
 * - 无参数：启动 JavaFX GUI。
 * - 有参数：处理命令行。
 * 用法：sanctum [&lt;命令&gt; &lt;库路径&gt; [参数]]
 * 命令：create / unlock / add / list / get / sync
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            // 无参数 → 启动 GUI
            SanctumGui.launch(args);
            return;
        }
        String cmd = args[0];
        switch (cmd) {
            case "create" -> {
                Path root = Path.of(requireArg(args, 1, "path"));
                Sanctum.createAndUnlock(root, readPassword("master password"));
                System.out.println("created " + root);
            }
            case "unlock" -> {
                Path root = Path.of(requireArg(args, 1, "path"));
                Sanctum s = Sanctum.open(root);
                s.unlock(readPassword("master password"));
                System.out.println("unlocked " + root + ", objects=" + s.store().list().size());
            }
            case "add" -> {
                Path root = Path.of(requireArg(args, 1, "path"));
                String name = requireArg(args, 2, "name");
                Sanctum s = Sanctum.open(root);
                s.unlock(readPassword("master password"));
                UUID uuid = s.createEntry(null, name, Map.of());
                System.out.println("added " + name + " -> " + uuid);
            }
            case "list" -> {
                Path root = Path.of(requireArg(args, 1, "path"));
                Sanctum s = Sanctum.open(root);
                s.unlock(readPassword("master password"));
                for (UUID u : s.store().list()) {
                    Json.Node n = s.getEntry(u);
                    System.out.println(u + " " + (n == null ? "?" : n.str("type") + "/" + n.str("name")));
                }
            }
            case "get" -> {
                Path root = Path.of(requireArg(args, 1, "path"));
                UUID uuid = UUID.fromString(requireArg(args, 2, "uuid"));
                Sanctum s = Sanctum.open(root);
                s.unlock(readPassword("master password"));
                Json.Node n = s.getEntry(uuid);
                System.out.println(n == null ? "not found" : Json.stringify(n));
            }
            case "sync" -> {
                Path root = Path.of(requireArg(args, 1, "path"));
                Sanctum s = Sanctum.open(root);
                s.unlock(readPassword("master password"));
                com.flora.sanctum.sync.SyncService sync = new com.flora.sanctum.sync.SyncService(root);
                if (!sync.isFullyManaged()) {
                    System.out.println("not fully managed, skip sync");
                    return;
                }
                s.close();
                sync.sync();
                System.out.println("synced " + root);
            }
            default -> usage();
        }
    }

    static char[] readPassword(String prompt) throws Exception {
        Console console = System.console();
        if (console != null) {
            char[] p = console.readPassword("%s: ", prompt);
            if (p == null) {
                throw new IllegalStateException("no password");
            }
            return p;
        }
        // 无控制台（如管道）则从环境变量读
        String env = System.getenv("SANCTUM_PASSWORD");
        if (env == null) {
            throw new IllegalStateException("SANCTUM_PASSWORD not set and no console");
        }
        return env.toCharArray();
    }

    static String requireArg(String[] args, int i, String what) {
        if (i >= args.length) {
            throw new IllegalArgumentException("missing " + what);
        }
        return args[i];
    }

    private static void usage() {
        System.out.println("Usage: sanctum [<create|unlock|add|list|get|sync> <path> [args...]]");
        System.out.println("  (no args -> GUI)");
    }
}
