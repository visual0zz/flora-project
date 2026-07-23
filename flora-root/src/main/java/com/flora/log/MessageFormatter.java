package com.flora.log;


/**
 * 消息格式化工具，将模板字符串中的 {@code {}} 占位符依次替换为参数值。
 * <p>包级私有，仅供日志框架内部使用。</p>
 */
final class MessageFormatter {

    private MessageFormatter() {
    }

    /**
     * 将模板中的 {@code {}} 占位符依次替换为参数值。
     *
     * @param template 消息模板，可能包含 {@code {}} 占位符
     * @param args     要替换的参数数组，多余参数将被忽略
     * @return 格式化后的字符串，模板为 null 时返回 null
     */
    static String format(String template, Object[] args) {
        if (template == null) {
            return null;
        }
        if (args == null || args.length == 0) {
            return template;
        }
        StringBuilder sb = new StringBuilder(template.length() + 32);
        int argIdx = 0;
        int i = 0;
        int len = template.length();

        while (i < len && argIdx < args.length) {
            int brace = template.indexOf("{}", i);
            if (brace < 0) {
                break;
            }
            sb.append(template, i, brace);
            Object arg = args[argIdx++];
            sb.append(arg != null ? arg : "null");
            i = brace + 2;
        }
        sb.append(template, i, len);

        
        return sb.toString();
    }
}
