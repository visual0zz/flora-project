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

    private final String sgr;

    ShellBackgroundColor(String sgr) {
        this.sgr = sgr;
    }

    String sgr() {
        return sgr;
    }

    /** 以本背景色包装文本：{@code ESC[<sgr>m<text>ESC[0m}。 */
    public String wrap(String text) {
        return Ansi.wrap(text, sgr);
    }
}
