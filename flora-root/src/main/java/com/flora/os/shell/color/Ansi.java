package com.flora.os.shell.color;

/**
 * ANSI 转义序列常量与基础拼接工具。
 * <p>所有转义码均以 {@code ESC[}（{@code \u001B[}）起始、{@code m} 结束（SGR 语义）。
 * 这里只负责把若干 SGR 参数拼成完整序列并包裹文本，平台相关的虚拟终端初始化见
 * {@link AnsiConsole}。</p>
 */
final class Ansi {

    /** ANSI 转义引导符（ESC，八进制 033）。 */
    static final String ESC = "\u001B[";

    /** SGR 重置码（恢复默认样式）。 */
    static final String RESET = ESC + "0m";

    private Ansi() {
    }

    /**
     * 用给定 SGR 参数（不含引导符与结束符）包裹文本。
     *
     * @param text 待包裹文本
     * @param sgr  一个或多个 SGR 参数，以 {@code ;} 分隔（如 {@code "1;31"}）；为空则仅返回原文本
     * @return {@code ESC[<sgr>m<text>ESC[0m}
     */
    static String wrap(String text, String sgr) {
        if (sgr == null || sgr.isEmpty()) {
            return text;
        }
        return ESC + sgr + "m" + text + RESET;
    }
}
