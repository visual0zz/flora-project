package com.flora.sanctum.app.command;

import com.flora.sanctum.model.Sanctum;

import java.io.Console;
import java.nio.file.Path;

/**
 * 命令通用工具：主密码读取（控制台或环境变量）与库打开。
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

    /**
     * 打开并解锁一个库（读主密码，finally 清密码）。
     *
     * @return 已解锁的 Sanctum（调用方负责 close）
     */
    static Sanctum openUnlocked(Path root) throws Exception {
        char[] pw = readPassword("master password");
        try {
            Sanctum s = Sanctum.open(root);
            s.unlock(pw);
            return s;
        } finally {
            java.util.Arrays.fill(pw, (char) 0);
        }
    }
}
