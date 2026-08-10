package com.flora.os.shell.color;

import com.flora.os.natives.ffm.Native;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 跨平台 ANSI 虚拟终端初始化。
 * <p>Unix/macOS 与 Windows Terminal / VS Code 集成终端原生解释 ANSI 转义码，无需处理。
 * 唯独 Windows 旧版 conhost（未开启虚拟终端处理的 {@code cmd.exe}）默认不解释 ANSI，
 * 需经 kernel32 的 {@code SetConsoleMode} 开启 {@code ENABLE_VIRTUAL_TERMINAL_PROCESSING}。</p>
 *
 * <p>本类持有一个进程级 {@link #initialized} 标志：首次调用 {@link #ensureVirtualTerminal()}
 * 时（通常在第一次彩色输出前由 {@link Style} 自动触发）检测 Windows 平台并开启 VT 模式，
 * 后续调用直接短路。非 Windows 平台或已初始化时均为无操作。VT 开启失败（如旧系统不支持）
 * 仅记录并忽略，退化为「转义码原样输出」，不会中断程序。</p>
 */
public final class AnsiConsole {

    /** 进程级初始化标志：保证 VT 模式至多开启一次。 */
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    /** Windows 控制台标准输出句柄伪值（STD_OUTPUT_HANDLE = -11）。 */
    private static final long STD_OUTPUT_HANDLE = -11L;

    /** kernel32 ENABLE_VIRTUAL_TERMINAL_PROCESSING 标志位。 */
    private static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;

    private AnsiConsole() {
    }

    /**
     * 确保当前控制台能解释 ANSI 转义码（幂等）。
     * <p>首次在未初始化的 Windows 平台上调用时开启 VT 模式；其余情况直接返回。
     * 调用方（如 {@link Style}）应在每次彩色输出前调用，本方法内部已做短路优化。</p>
     */
    public static void ensureVirtualTerminal() {
        if (initialized.get()) {
            return;
        }
        if (!isWindows()) {
            initialized.set(true);
            return;
        }
        try {
            long handle = Native.callLong("kernel32", "GetStdHandle", STD_OUTPUT_HANDLE);
            int previous = Native.callInt("kernel32", "GetConsoleMode", (int) handle);
            int mode = previous | ENABLE_VIRTUAL_TERMINAL_PROCESSING;
            Native.callInt("kernel32", "SetConsoleMode", (int) handle, mode);
            initialized.set(true);
        } catch (Throwable ignored) {
            // VT 开启失败（旧系统/重定向/无控制台）：退化为转义码原样输出，不影响程序
            initialized.set(true);
        }
    }

    /** 当前是否已完成初始化（含非 Windows 平台的惰性置位），主要用于测试断言。 */
    static boolean isInitialized() {
        return initialized.get();
    }

    /** 重置初始化标志（测试用，模拟首次调用场景）。 */
    static void resetForTesting() {
        initialized.set(false);
    }

    /** 是否运行于 Windows 平台（委托 {@link com.flora.os.OsUtil}）。 */
    static boolean isWindows() {
        return com.flora.os.OsUtil.isWindows();
    }

    /** 组装 SGR 参数序列（各参数以 {@code ;} 分隔），供 {@link Ansi} 拼接完整转义序列。 */
    static String joinSgr(List<String> parts) {
        List<String> nonEmpty = new ArrayList<>(parts.size());
        for (String p : parts) {
            if (p != null && !p.isEmpty()) {
                nonEmpty.add(p);
            }
        }
        return String.join(";", nonEmpty);
    }
}
