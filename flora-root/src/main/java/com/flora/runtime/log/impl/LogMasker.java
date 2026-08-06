package com.flora.runtime.log.impl;

import com.flora.runtime.log.spi.Masker;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


/**
 * 默认日志脱敏实现：基于一组正则规则，将常见敏感信息掩盖为 {@code ********}。
 * <p>
 * 规则覆盖：Bearer 令牌、API key/secret/token 等赋值、URL 中的用户口令、
 * 邮箱、身份证号、信用卡号以及长度不小于 32 的随机串（疑似密钥/哈希）。
 * 默认规则可通过 {@link #withRule(String, String)} 扩展，不影响 {@link #DEFAULT} 常量。
 * </p>
 */
public final class LogMasker implements Masker {

    /**
     * 默认规则实例，覆盖常见密钥与 PII。
     */
    public static final LogMasker DEFAULT = new LogMasker();

    private final List<Rule> rules = new ArrayList<>();

    /**
     * 构造一个带默认规则集的脱敏器。
     */
    public LogMasker() {
        addDefaultRules();
    }

    private void addDefaultRules() {
        // Bearer 令牌
        add("(?<![A-Za-z0-9])Bearer\\s+[A-Za-z0-9._\\-]+", "Bearer ********");
        // API key / secret / token / password 等赋值，保留键名只掩盖值
        add("(?i)(api[_\\-]?key|secret|token|passwd|password|pwd|access[_\\-]?token|refresh[_\\-]?token)\\s*[:=]\\s*[\"']?[A-Za-z0-9._\\-/+]{8,}",
                "$1=********");
        // URL 中的 user:pass@ 凭据，保留协议名
        add("([a-zA-Z][a-zA-Z0-9+\\-.]*)://[^\\u0000-\\u001f /:]+:[^\\u0000-\\u001f /:]+@",
                "$1://********@");
        // 邮箱
        add("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}", "****@****");
        // 身份证号（18 位）
        add("\\b\\d{17}[\\dXx]\\b", "********");
        // 信用卡号（13-19 位，可含空格或连字符；分隔符仅在其后仍为数字时消费，避免吞掉词间空格）
        add("\\b\\d(?:[ \\-]?\\d){12,18}\\b", "********");
        // 长随机串（>=32 连续字符，疑似密钥/令牌/哈希）
        add("\\b[A-Za-z0-9+/_\\-]{32,}\\b", "********");
    }

    private void add(String regex, String replacement) {
        rules.add(new Rule(Pattern.compile(regex), replacement));
    }

    /**
     * 追加一条自定义规则，返回包含默认规则与新规则的新实例。
     * <p>不会修改当前实例，因而 {@link #DEFAULT} 常量可安全共享。</p>
     *
     * @param regex       匹配敏感片段的正则
     * @param replacement 替换文本（支持 {@code $1} 等组引用）
     * @return 新的脱敏器实例
     */
    public LogMasker withRule(String regex, String replacement) {
        LogMasker next = new LogMasker();
        next.rules.add(new Rule(Pattern.compile(regex), replacement));
        return next;
    }

    @Override
    public String mask(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        for (Rule rule : rules) {
            result = rule.pattern.matcher(result).replaceAll(rule.replacement);
        }
        return result;
    }


    private static final class Rule {
        final Pattern pattern;
        final String replacement;

        Rule(Pattern pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }
    }
}
