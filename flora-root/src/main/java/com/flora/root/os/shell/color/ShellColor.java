package com.flora.root.os.shell.color;

/**
 * 终端前景色枚举，每种颜色持有对应的 ANSI SGR 转义码。
 * <p>与 {@link ShellBackgroundColor}、{@link ShellStyle} 组合使用：
 * 直接 {@link #wrap(String)} 仅上前景色，或经 {@link Style} 构建器组合前景/背景/样式。
 * ANSI 转义码为 ECMA-48 标准，跨平台一致；Windows 旧控制台需在首次输出前由
 * {@link AnsiConsole#ensureVirtualTerminal()} 开启虚拟终端处理模式方能解释。</p>
 */
public enum ShellColor {

    BLACK("30"),
    RED("31"),
    GREEN("32"),
    YELLOW("33"),
    BLUE("34"),
    PURPLE("35"),
    SKY("36"),
    WHITE("37");

    /** ANSI 转义引导符（ESC，八进制 033）。 */
    private static final String ESC = "\u001B[";
    /** SGR 重置码（恢复默认样式）。 */
    private static final String RESET = "\u001B[0m";

    /** SGR 参数（不含转义前缀与后缀），如 {@code "31"}。 */
    private final String sgr;

    ShellColor(String sgr) {
        this.sgr = sgr;
    }

    /** 返回不含包裹的 SGR 参数（供 {@link Style} 拼接完整转义序列）。 */
    String sgr() {
        return sgr;
    }

    /** 以本前景色包装文本：{@code ESC[<sgr>m<text>ESC[0m}。 */
    public String wrap(String text) {
        return ESC + sgr + "m" + text + RESET;
    }
}
