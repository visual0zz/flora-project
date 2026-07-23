package com.flora.log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


/**
 * 日志布局格式器，根据模式字符串格式化日志事件输出。
 * <p>
 * 支持的转换符：%d（日期）、%t/%thread（线程）、%p/%level（级别）、
 * %c/%logger（日志器名称）、%m/%msg/%message（消息）、
 * %mdc{key}（MDC 上下文）、%n（换行）、%%（百分号）。
 * 支持宽度和左对齐（如 %-5level）。
 */
public class Layout {

    
    private static final Map<String, Converter> CONVERTERS = new HashMap<>();
    
    private static final Map<Character, String> ALIASES = new HashMap<>();

    static {
        CONVERTERS.put("d", Layout::appendDate);
        CONVERTERS.put("date", Layout::appendDate);
        CONVERTERS.put("t", Layout::appendThread);
        CONVERTERS.put("thread", Layout::appendThread);
        CONVERTERS.put("p", Layout::appendLevel);
        CONVERTERS.put("level", Layout::appendLevel);
        CONVERTERS.put("le", Layout::appendLevel);
        CONVERTERS.put("c", Layout::appendLogger);
        CONVERTERS.put("logger", Layout::appendLogger);
        CONVERTERS.put("lo", Layout::appendLogger);
        CONVERTERS.put("m", Layout::appendMessage);
        CONVERTERS.put("msg", Layout::appendMessage);
        CONVERTERS.put("message", Layout::appendMessage);
        CONVERTERS.put("mdc", Layout::appendMdc);
        CONVERTERS.put("n", Layout::appendNewline);
        CONVERTERS.put("%", Layout::appendPercent);
    }

    private final String pattern;
    private final Converter[] converters;
    private final boolean[] leftAligns;
    private final int[] widths;
    private final String[] optionTexts;

    /**
     * 构造一个布局格式器。
     *
     * @param pattern 布局模式字符串，如 "%d{HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n"
     */
    public Layout(String pattern) {
        this.pattern = pattern;
        
        var parsed = parse(pattern);
        this.converters = parsed.converters;
        this.leftAligns = parsed.leftAligns;
        this.widths = parsed.widths;
        this.optionTexts = parsed.optionTexts;
    }

    /**
     * 获取布局模式字符串。
     *
     * @return 模式字符串
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * 格式化日志事件，按照布局模式生成输出字符串。
     *
     * @param event 日志事件
     * @return 格式化后的字符串
     */
    public String format(LogEvent event) {
        StringBuilder sb = new StringBuilder(128);
        int len = converters.length;
        for (int i = 0; i < len; i++) {
            converters[i].apply(sb, event, leftAligns[i], widths[i], optionTexts[i]);
        }
        return sb.toString();
    }

    

    /**
     * 解析模式字符串，生成转换器数组及其对应的对齐方式、宽度和选项。
     *
     * @param pattern 模式字符串
     * @return 解析结果，包含转换器数组和对齐/宽度/选项信息
     */
    private static ParseResult parse(String pattern) {
        int len = pattern.length();
        java.util.List<Converter> convList = new java.util.ArrayList<>();
        java.util.List<Boolean> alignList = new java.util.ArrayList<>();
        java.util.List<Integer> widthList = new java.util.ArrayList<>();
        java.util.List<String> optionList = new java.util.ArrayList<>();

        int i = 0;
        
        StringBuilder literal = new StringBuilder();

        while (i < len) {
            char c = pattern.charAt(i);
            if (c == '%') {
                
                flushLiteral(literal, convList, alignList, widthList, optionList);

                i++;
                if (i >= len) break;

                
                boolean left = false;
                int w = 0;
                if (pattern.charAt(i) == '-') {
                    left = true;
                    i++;
                }
                while (i < len && pattern.charAt(i) >= '0' && pattern.charAt(i) <= '9') {
                    w = w * 10 + (pattern.charAt(i) - '0');
                    i++;
                }
                if (i >= len) break;

                
                int start = i;
                while (i < len && Character.isLetter(pattern.charAt(i))) {
                    i++;
                }
                String word = pattern.substring(start, i);

                
                String option = "";
                if (i < len && pattern.charAt(i) == '{') {
                    int end = pattern.indexOf('}', i);
                    if (end > i) {
                        option = pattern.substring(i + 1, end);
                        i = end + 1;
                    }
                }

                
                String canon = CONVERTERS.containsKey(word) ? word : null;
                if (canon == null && word.length() == 1) {
                    
                    char aliasChar = word.charAt(0);
                    
                }

                Converter conv = CONVERTERS.get(word);
                if (conv == null && word.length() == 1) {
                    char ac = word.charAt(0);
                    if (ac == '%') {
                        conv = Layout::appendPercent;
                    } else if (ac == 'n') {
                        conv = Layout::appendNewline;
                    }
                }

                if (conv != null) {
                    convList.add(conv);
                    alignList.add(left);
                    widthList.add(w);
                    optionList.add(option);
                } else {
                    
                    literal.append('%');
                    if (left) literal.append('-');
                    if (w > 0) literal.append(w);
                    literal.append(word);
                    if (!option.isEmpty()) {
                        literal.append('{').append(option).append('}');
                    }
                }
            } else {
                literal.append(c);
                i++;
            }
        }
        flushLiteral(literal, convList, alignList, widthList, optionList);

        return new ParseResult(
                convList.toArray(new Converter[0]),
                toPrimitive(alignList),
                toIntPrimitive(widthList),
                optionList.toArray(new String[0]));
    }

    /**
     * 将累积的纯文本作为字面量转换器刷新到列表中。
     *
     * @param literal 累积的文字缓冲区
     * @param convs   转换器列表
     * @param aligns  对齐标志列表
     * @param widths  宽度列表
     * @param opts    选项列表
     */
    private static void flushLiteral(StringBuilder literal,
                                      java.util.List<Converter> convs,
                                      java.util.List<Boolean> aligns,
                                      java.util.List<Integer> widths,
                                      java.util.List<String> opts) {
        if (literal.length() > 0) {
            String lit = literal.toString();
            convs.add((sb, e, a, w, o) -> sb.append(lit));
            aligns.add(false);
            widths.add(0);
            opts.add("");
            literal.setLength(0);
        }
    }

    private static boolean[] toPrimitive(java.util.List<Boolean> list) {
        boolean[] arr = new boolean[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    private static int[] toIntPrimitive(java.util.List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    private record ParseResult(Converter[] converters, boolean[] leftAligns, int[] widths, String[] optionTexts) {
    }

    

    @FunctionalInterface
    private interface Converter {
        void apply(StringBuilder sb, LogEvent event, boolean leftAlign, int width, String option);
    }

    

    /**
     * 追加日期时间到缓冲区。
     *
     * @param sb    字符串缓冲区
     * @param event 日志事件
     * @param left  是否左对齐
     * @param width 宽度
     * @param option 日期格式，为空时使用默认格式 "yyyy-MM-dd HH:mm:ss.SSS"
     */
    private static void appendDate(StringBuilder sb, LogEvent event, boolean left, int width, String option) {
        String pattern = option.isEmpty() ? "yyyy-MM-dd HH:mm:ss.SSS" : option;
        sb.append(new SimpleDateFormat(pattern).format(new Date(event.getTimestamp())));
    }

    /**
     * 追加线程名称到缓冲区。
     */
    private static void appendThread(StringBuilder sb, LogEvent event, boolean left, int width, String option) {
        sb.append(event.getThread().getName());
    }

    /**
     * 追加日志级别名称到缓冲区，支持宽度和对齐。
     */
    private static void appendLevel(StringBuilder sb, LogEvent event, boolean left, int width, String option) {
        String level = event.getLevel().name();
        if (width > 0) {
            if (left) {
                sb.append(level);
                pad(sb, ' ', width - level.length());
            } else {
                pad(sb, ' ', width - level.length());
                sb.append(level);
            }
        } else {
            sb.append(level);
        }
    }

    /**
     * 追加日志器名称到缓冲区，支持缩写（通过 option 指定最大长度）。
     */
    private static void appendLogger(StringBuilder sb, LogEvent event, boolean left, int width, String option) {
        String name = event.getLoggerName();
        if (!option.isEmpty()) {
            int len = Integer.parseInt(option);
            if (len > 0 && name.length() > len) {
                name = abbreviate(name, len);
            }
        }
        sb.append(name);
    }

    /**
     * 追加消息内容到缓冲区。
     */
    private static void appendMessage(StringBuilder sb, LogEvent event, boolean left, int width, String option) {
        String msg = event.getFormattedMessage();
        if (msg != null) sb.append(msg);
    }

    /**
     * 追加 MDC 上下文值到缓冲区。
     *
     * @param option MDC 键名
     */
    private static void appendMdc(StringBuilder sb, LogEvent event, boolean left, int width, String option) {
        String val = MDC.get(option);
        if (val != null) sb.append(val);
    }

    /**
     * 追加系统换行符到缓冲区。
     */
    private static void appendNewline(StringBuilder sb, LogEvent event, boolean left, int width, String option) {
        sb.append(System.lineSeparator());
    }

    /**
     * 追加百分号字符到缓冲区（转义 %%）。
     */
    private static void appendPercent(StringBuilder sb, LogEvent event, boolean left, int width, String option) {
        sb.append('%');
    }

    

    /**
     * 在缓冲区中追加指定数量的填充字符。
     *
     * @param sb    字符串缓冲区
     * @param c     填充字符
     * @param count 填充次数
     */
    private static void pad(StringBuilder sb, char c, int count) {
        for (int i = 0; i < count; i++) sb.append(c);
    }

    /**
     * 缩写日志器名称，保留每个包名的首字母和最后一个类名。
     * <p>
     * 例如 "com.example.foo.BarService" 缩写为 "c.e.f.BarService"。
     * 如果缩写后仍然超过 maxLen，则截取末尾部分。
     *
     * @param name   原始日志器名称
     * @param maxLen 最大长度
     * @return 缩写后的名称
     */
    static String abbreviate(String name, int maxLen) {
        String[] parts = name.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < parts.length; j++) {
            if (j < parts.length - 1) {
                sb.append(parts[j].charAt(0)).append('.');
            } else {
                sb.append(parts[j]);
            }
        }
        String abbr = sb.toString();
        if (abbr.length() <= maxLen) return abbr;
        return abbr.substring(abbr.length() - maxLen);
    }
}
