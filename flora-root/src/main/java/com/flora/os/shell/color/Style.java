package com.flora.os.shell.color;

import java.util.ArrayList;
import java.util.List;

/**
 * ANSI 样式构建器：组合前景色、背景色与文本样式，产出被完整转义序列包裹的文本。
 * <p>典型用法：
 * <pre>{@code
 * String s = Style.of(ShellColor.RED)
 *                  .on(ShellBackgroundColor.WHITE)
 *                  .with(ShellStyle.BOLD)
 *                  .wrap("告警");
 * // 等效于 ShellColor.RED.wrap("告警")（仅前景色时）
 * }</pre>
 * 每次 {@link #wrap(String)} 前自动触发 {@link AnsiConsole#ensureVirtualTerminal()}，
 * 在 Windows 旧控制台首次输出时开启虚拟终端处理模式，调用方无感知。</p>
 */
public final class Style {

    /** ANSI 转义引导符（ESC，八进制 033）。 */
    private static final String ESC = "\u001B[";
    /** SGR 重置码（恢复默认样式）。 */
    private static final String RESET = "\u001B[0m";

    private ShellColor foreground;
    private ShellBackgroundColor background;
    private final List<ShellStyle> styles = new ArrayList<>();

    private Style() {
    }

    /** 以指定前景色起手构建样式。 */
    public static Style of(ShellColor foreground) {
        Style s = new Style();
        s.foreground = foreground;
        return s;
    }

    /** 起手一个无任何样式的空构建器（{@link #wrap(String)} 将返回原文本）。 */
    public static Style empty() {
        return new Style();
    }

    /** 设置背景色（可链式追加）。 */
    public Style on(ShellBackgroundColor background) {
        this.background = background;
        return this;
    }

    /** 追加一个文本样式（可多次调用叠加）。 */
    public Style with(ShellStyle style) {
        styles.add(style);
        return this;
    }

    /**
     * 用当前组合样式包裹文本。
     * <p>包裹前确保控制台已开启 ANSI 解释（Windows 旧控制台首次调用时自动初始化 VT 模式）。</p>
     *
     * @param text 待包裹文本
     * @return {@code ESC[<fg>;<bg>;<styles>m<text>ESC[0m}；无任何样式时返回原文本
     */
    public String wrap(String text) {
        AnsiConsole.ensureVirtualTerminal();
        List<String> parts = new ArrayList<>(2 + styles.size());
        if (foreground != null) {
            parts.add(foreground.sgr());
        }
        if (background != null) {
            parts.add(background.sgr());
        }
        for (ShellStyle st : styles) {
            parts.add(st.sgr());
        }
        String joined = AnsiConsole.joinSgr(parts);
        if (joined.isEmpty()) {
            return text;
        }
        return ESC + joined + "m" + text + RESET;
    }
}
