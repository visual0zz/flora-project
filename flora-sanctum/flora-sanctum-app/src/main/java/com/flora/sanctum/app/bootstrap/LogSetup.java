package com.flora.sanctum.app.bootstrap;

import com.flora.root.runtime.log.Level;
import com.flora.root.runtime.log.LogConfig;
import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 应用日志初始化。
 * <p>
 * 基于 flora-root 日志门面，将日志写入符合 XDG 规范的状态目录：
 * 优先使用 {@code $XDG_STATE_HOME}，否则回退到 {@code ~/.local/state}；
 * 应用日志固定落在其中的 {@code sanctum/} 子目录。
 * 当前文件为 {@code sanctum.log}，跨天或达尺寸上限后按序号滚动归档，
 * 归档名形如 {@code sanctum-2026-08-31.1.log}（日期取自滚动当日，序号 1 为最新，每跨天归零），
 * {@code maxHistory(10)} 为跨所有日期的全局保留上限，单个上限 10 MiB，目录总占用封顶约 110 MiB。
 * <p>
 * 同时安装全局未捕获异常处理器，使任何线程抛出的未处理异常都被记录为 FATAL 级，
 * 便于桌面程序崩溃后从日志文件回溯现场。
 */
public final class LogSetup {

    private static final Logger LOG = LoggerFactory.getLogger(LogSetup.class);

    private static final String APP_NAME = "sanctum";

    private LogSetup() {
    }

    /**
     * 安装日志系统。应在应用启动的最早期调用（GUI 启动之前）。
     * 目录创建失败时退化为当前工作目录下的日志文件，不阻断启动。
     */
    public static void install() {
        Path stateDir = resolveStateDir();
        try {
            Files.createDirectories(stateDir);
        } catch (IOException e) {
            System.err.println("Failed to create log directory " + stateDir + ": " + e.getMessage());
        }
        Path logFile = stateDir.resolve(APP_NAME + ".log");

        LogConfig.configure(c -> c
                .rootLevel(Level.INFO)
                .rollingFile(rc -> rc
                        .file(logFile.toString())
                        .pattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger - %msg%n")
                        .datePattern("yyyy-MM-dd")
                        .filePattern(APP_NAME + "-%d{yyyy-MM-dd}.%i.log")
                        .maxSize(10L * 1024 * 1024)
                        .maxHistory(10)));

        installUncaughtExceptionHandler();
        LOG.info("Logging initialized, file={}", logFile);
    }

    /**
     * 解析 XDG 状态目录：优先 {@code $XDG_STATE_HOME}，未设置时回退 {@code ~/.local/state}。
     */
    private static Path resolveStateDir() {
        String xdg = System.getenv("XDG_STATE_HOME");
        Path base;
        if (xdg != null && !xdg.isBlank()) {
            base = Path.of(xdg);
        } else {
            String home = System.getProperty("user.home");
            base = Path.of(Objects.requireNonNullElse(home, "."), ".local", "state");
        }
        return base.resolve(APP_NAME);
    }

    private static void installUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                LOG.fatal("Uncaught exception in thread " + thread.getName(), throwable));
    }
}
