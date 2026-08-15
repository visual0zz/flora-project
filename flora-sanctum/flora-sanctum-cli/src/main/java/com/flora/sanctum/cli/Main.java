package com.flora.sanctum.cli;

import com.flora.sanctum.model.Json;
import com.flora.sanctum.model.Sanctum;

import java.io.Console;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * flora-sanctum 命令行入口（阶段 5，作为 core 冒烟测试）。
 * <p>
 * 用法：sanctum &lt;命令&gt; &lt;库路径&gt; [参数]
 * 命令：create / unlock / add / list / get
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage();
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
            default -> usage();
        }
    }

    private static char[] readPassword(String prompt) throws Exception {
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

    private static String requireArg(String[] args, int i, String what) {
        if (i >= args.length) {
            throw new IllegalArgumentException("missing " + what);
        }
        return args[i];
    }

    private static void usage() {
        System.out.println("Usage: sanctum <create|unlock|add|list|get> <path> [args...]");
    }
}
