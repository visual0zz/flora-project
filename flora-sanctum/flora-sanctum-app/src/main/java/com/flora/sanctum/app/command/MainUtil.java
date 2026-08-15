package com.flora.sanctum.app.command;

import java.io.Console;

/**
 * 命令通用工具：主密码读取（控制台或环境变量）。
 */
public final class MainUtil {

    private MainUtil() {
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
}
