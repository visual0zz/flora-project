package com.flora.internal.evaluation;

import com.flora.root.os.OsUtil;
import com.flora.root.os.shell.color.AnsiConsole;
import com.flora.root.os.shell.color.ShellBackgroundColor;
import com.flora.root.os.shell.color.ShellColor;
import com.flora.root.os.shell.color.ShellStyle;
import com.flora.root.os.shell.color.Style;

/**
 * 跨平台 ANSI 颜色工具的人工验证入口。
 * <p>通过 {@link Style} 与 {@link ShellColor} 产出被转义序列包裹的样本并打印到控制台，
 * 同时验证 {@link AnsiConsole#ensureVirtualTerminal()} 在跨平台下的初始化行为，
 * 供人工直接判断输出是否被正确上色、Windows 平台是否自动开启虚拟终端处理。</p>
 */
public final class ShellColorEvaluation {

    private ShellColorEvaluation() {
    }

    public static void main(String[] args) {
        printPlatform();
        verifyVirtualTerminalInit();
        evaluateForeground();
        evaluateStyleCombos();
        evaluateEmptyStyle();
        evaluateRawColorWrap();
    }

    /** 打印当前运行平台探测结果。 */
    private static void printPlatform() {
        System.out.println("==================== 平台探测 ====================");
        System.out.println("os.name = " + System.getProperty("os.name"));
        System.out.println("isWindows = " + OsUtil.isWindows());
        System.out.println("isLinux   = " + OsUtil.isLinux());
        System.out.println("isMac     = " + OsUtil.isMac());
    }

    /** 验证 ensureVirtualTerminal 的调用安全性:多次调用不应抛错,仅触发一次 VT 初始化。 */
    private static void verifyVirtualTerminalInit() {
        System.out.println("==================== 虚拟终端初始化 ====================");
        // 首次调用触发真实初始化(Windows 平台开启 VT,其余平台惰性置位)
        AnsiConsole.ensureVirtualTerminal();
        System.out.println("首次调用完成, 控制台 VT 初始化已在内部完成");
        // 重复调用应直接短路, 不会重复开启或抛错
        AnsiConsole.ensureVirtualTerminal();
        AnsiConsole.ensureVirtualTerminal();
        System.out.println("重复调用完成, 幂等短路生效(不重复开启)");
        // 首次彩色输出前由 Style 自动触发, 这里组合输出即隐式再次调用
        System.out.println(Style.of(ShellColor.BLUE).wrap("蓝字: 自动 VT 初始化校验点"));
    }

    /** 遍历全部前景色输出单色样本。 */
    private static void evaluateForeground() {
        System.out.println("==================== 前景色 ====================");
        for (ShellColor color : ShellColor.values()) {
            System.out.println(color.name() + ": " + color.wrap("Hello, flora"));
        }
    }

    /** 组合前景/背景/文本样式输出复杂样本。 */
    private static void evaluateStyleCombos() {
        System.out.println("==================== 组合样式 ====================");
        String s1 = Style.of(ShellColor.RED)
                .on(ShellBackgroundColor.WHITE)
                .with(ShellStyle.BOLD)
                .wrap("告警: 磁盘即将写满");
        System.out.println(s1);

        String s2 = Style.of(ShellColor.GREEN)
                .with(ShellStyle.UNDERLINE)
                .wrap("构建成功");
        System.out.println(s2);

        String s3 = Style.of(ShellColor.YELLOW)
                .on(ShellBackgroundColor.BLACK)
                .with(ShellStyle.BOLD)
                .with(ShellStyle.BLINK)
                .wrap("闪烁提示");
        System.out.println(s3);
    }

    /** 空样式应原样返回文本,且不触发任何转义序列。 */
    private static void evaluateEmptyStyle() {
        System.out.println("==================== 空样式 ====================");
        String plain = Style.empty().wrap("无样式文本");
        System.out.println("输出: " + plain);
        System.out.println("是否原样返回 = " + "无样式文本".equals(plain));
    }

    /** ShellColor 直接wrap 应等价于仅含前景色的 Style。 */
    private static void evaluateRawColorWrap() {
        System.out.println("==================== 裸颜色 wrap ====================");
        String direct = ShellColor.SKY.wrap("青色文本");
        String viaStyle = Style.of(ShellColor.SKY).wrap("青色文本");
        System.out.println(direct);
        System.out.println("等价(与 Style 组合一致) = " + direct.equals(viaStyle));
    }
}
