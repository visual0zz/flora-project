package com.flora.sanctum.app.bootstrap;

import com.flora.root.runtime.log.Level;
import com.flora.root.runtime.log.LogConfig;
import com.flora.root.runtime.log.LogMaskers;
import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;
import com.flora.root.runtime.log.spi.Masker;

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

    /**
     * 行格式：时间戳、线程、级别、logger、消息，末段 {@code %ex} 追加关联异常的完整堆栈
     * （无异常时为空）。缺了 {@code %ex} 则 {@code LOG.error(msg, throwable)} 关联的堆栈会被静默丢弃。
     */
    private static final String FILE_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger - %msg%n%ex";

    /**
     * 路径脱敏：掩盖用户主目录下的用户名段，避免日志泄露 OS 用户名与保险库位置。
     * {@link LogMaskers#DEFAULT} 已覆盖 URL 凭据/令牌/邮箱等，此处仅补足绝对路径这一缺口。
     */
    private static final Masker PATH_MASKER = text -> {
        if (text == null) {
            return null;
        }
        String r = text.replaceAll("([A-Za-z]:\\\\.+\\\\Users\\\\)[^\\\\]+", "$1****");
        r = r.replaceAll("(/Users/|/home/)[^/]+", "$1****");
        return r;
    };

    /**
     * URL 凭据脱敏：默认规则集要求 {@code user:pass@} 两段，会漏掉 GitHub 等常用的
     * {@code scheme://<token>@host}（令牌作用户名、无 password）形式。此处补足该高危场景。
     */
    private static final Masker URL_MASKER = text -> {
        if (text == null) {
            return null;
        }
        return text.replaceAll("([a-zA-Z][a-zA-Z0-9+\\-.]*)://[^\\s/@]+@", "$1://********@");
    };

    private LogSetup() {
    }

    /**
     * 安装日志系统。应在应用启动的最早期调用（GUI 启动之前）。
     * 目录创建失败时退化为仅输出到标准输出的日志（不写文件），不阻断启动。
     */
    public static void install() {
        Path stateDir = resolveStateDir();
        Path logFile = stateDir.resolve(APP_NAME + ".log");
        boolean dirOk = createLogDir(stateDir);

        LogConfig.configure(c -> {
            c.rootLevel(Level.INFO);
            if (dirOk) {
                c.rollingFile(rc -> rc
                        .file(logFile.toString())
                        .pattern(FILE_PATTERN)
                        .datePattern("yyyy-MM-dd")
                        .filePattern(APP_NAME + "-%d{yyyy-MM-dd}.%i.log")
                        .maxSize(10L * 1024 * 1024)
                        .maxHistory(10));
            } else {
                c.console(cc -> cc.pattern(FILE_PATTERN));
            }
            // 开启全局日志脱敏：默认规则集 + URL 凭据（含 token@ 形式）+ 绝对路径用户名段掩盖
            c.mask(LogMaskers.DEFAULT, URL_MASKER, PATH_MASKER);
        });

        installUncaughtExceptionHandler();
        LOG.info("Logging initialized, target={}", dirOk ? logFile : "stdout");
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

    /**
     * 创建日志目录；失败仅告警并返回 false（调用方据此退化为标准输出日志），不抛异常阻断启动。
     */
    private static boolean createLogDir(Path stateDir) {
        try {
            Files.createDirectories(stateDir);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to create log directory " + stateDir + ": " + e.getMessage()
                    + "; falling back to stdout logging.");
            return false;
        }
    }

    private static void installUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                LOG.fatal("Uncaught exception in thread " + thread.getName(), throwable));
    }
}
