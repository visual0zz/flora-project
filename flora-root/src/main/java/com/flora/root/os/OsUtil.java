package com.flora.root.os;

import java.util.Locale;

/**
 * 操作系统平台探测工具。
 * <p>基于标准系统属性 {@code os.name} 判断运行平台，供平台相关逻辑（如 Windows 控制台
 * 虚拟终端初始化）做分支。探测结果属进程级不变量，无状态、线程安全。</p>
 */
public final class OsUtil {

    private OsUtil() {
    }

    /** 是否运行于 Windows 平台。 */
    public static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }

    /** 是否运行于 Linux 平台。 */
    public static boolean isLinux() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("linux");
    }

    /** 是否运行于 macOS 平台。 */
    public static boolean isMac() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("mac");
    }
}
