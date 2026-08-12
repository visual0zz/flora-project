package com.flora.root.os.shell.color;

/**
 * 终端文本样式枚举（高亮、下划线、闪烁、反显、隐藏），持有对应的 ANSI SGR 转义码。
 * <p>与 {@link ShellColor}/{@link ShellBackgroundColor} 经 {@link Style} 构建器组合使用。</p>
 */
public enum ShellStyle {

    /** 高亮 / 粗体（SGR 1）。 */
    BOLD("1"),
    /** 下划线（SGR 4）。 */
    UNDERLINE("4"),
    /** 闪烁（SGR 5）。 */
    BLINK("5"),
    /** 反显（SGR 7）。 */
    REVERSE("7"),
    /** 隐藏（SGR 8）。 */
    HIDE("8");

    /** SGR 参数（不含转义前缀与后缀），如 {@code "1"}。 */
    private final String sgr;

    ShellStyle(String sgr) {
        this.sgr = sgr;
    }

    String sgr() {
        return sgr;
    }
}
