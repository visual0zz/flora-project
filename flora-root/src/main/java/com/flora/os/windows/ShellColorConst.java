package com.flora.os.windows;

/**
 * Shell 终端颜色常量接口，定义 ANSI 转义码的颜色和样式常量。
 * 提供终端文本颜色（前景色、背景色）以及文本样式（高亮、下划线等）。
 */
public interface ShellColorConst {
    String clear="\033[0m";
    String hightlight="\033[01m";
    String underline="\033[04m";
    String blinking="\033[05m";
    String reverse="\033[07m";
    String fading="\033[08m";
    String cls="\033[2Jm";
    String hide_cursor="\033[?25l";
    String show_cursor="\033[?25h";

    String black="\033[30m";
    String red="\033[31m";
    String green="\033[32m";
    String yellow="\033[33m";
    String blue="\033[34m";
    String purple="\033[35m";
    String sky ="\033[36m";
    String white="\033[37m";

    String black_background="\033[40m";
    String red_background="\033[41m";
    String green_background="\033[42m";
    String yellow_background="\033[43m";
    String blue_background="\033[44m";
    String purple_background="\033[45m";
    String deep_green_background="\033[46m";
    String white_background="\033[47m";

    /**
     * 根据颜色名称获取对应的 ANSI 颜色代码（带高亮样式）。
     *
     * @param name 颜色名称（red, black, green, yellow, blue, purple, white, sky）
     * @return ANSI 颜色代码字符串
     */
    static String getColorCode(String name){
        switch (name){
            case "red":return red+hightlight;
            case "black":return black+hightlight;
            case "green":return green+hightlight;
            case "yellow":return yellow+hightlight;
            case "blue":return blue+hightlight;
            case "purple":return purple+hightlight;
            case "white":return white+hightlight;
            case "sky":return sky +hightlight;
        }
        return "";
    }
}
