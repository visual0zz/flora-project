package com.flora.os.shell.color;

/**
 * 终端背景色枚举，每种背景色持有对应的 ANSI SGR 转义码（40–47 区间）。
 * <p>通常与 {@link ShellColor} 前景色经 {@link Style} 构建器组合使用，
 * 也可 {@link #wrap(String)} 单独设置背景色。</p>
 */
public enum ShellBackgroundColor {

    BLACK("40"),
    RED("41"),
    GREEN("42"),
    YELLOW("43"),
    BLUE("44"),
    PURPLE("45"),
    DEEP_GREEN("46"),
    WHITE("47");

    /** ANSI 转义引导符（ESC，八进制 033）。 */
    private static final String ESC = "\u001B[";
    /** SGR 重置码（恢复默认样式）。 */
    private static final String RESET = "\u001B[0m";

    /** SGR 参数（不含转义前缀与后缀），如 {@code "41"}。 */
    private final String sgr;

    ShellBackgroundColor(String sgr) {
        this.sgr = sgr;
    }

    String sgr() {
        return sgr;
    }

    /** 以本背景色包装文本：{@code ESC[<sgr>m<text>ESC[0m}。 */
    public String wrap(String text) {
        return ESC + sgr + "m" + text + RESET;
    }
}
