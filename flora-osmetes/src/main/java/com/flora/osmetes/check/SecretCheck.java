package com.flora.osmetes.check;

import com.flora.osmetes.CheckIssue;
import com.flora.osmetes.Severity;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 密钥检查项：扫描文本文件中疑似硬编码的密钥、口令、令牌等敏感配置。
 * <p>
 * 通过特征键名（password / secret / token / apiKey / privateKey 等）后紧跟
 * 赋值（值可为引号包围或裸值）来识别，值需为非空且非占位符。报告精确位置
 * 并收集一个文件内的全部命中。
 */
public final class SecretCheck extends LineCheck {

    private static final Set<String> EXTENSIONS = Set.of(
            ".java", ".properties", ".yaml", ".yml", ".json", ".xml", ".kts", ".gradle");

    /** 敏感键名特征：形如 xxxxPassword / xxxxSecret / xxxxToken / apiKey / accessKey 等。 */
    private static final Pattern KEY_NAME = Pattern.compile(
            "(?i)\\b(?:password|passwd|pwd|secret|token|apikey|access[_-]?key|secret[_-]?key|"
                    + "private[_-]?key|auth[_-]?token|api[_-]?secret)\\b");

    /** 捕获形如 key = "value" / key: "value" 且 value 非空非占位符的赋值。 */
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "([A-Za-z0-9_.-]*" + KEY_NAME.pattern() + "[A-Za-z0-9_.-]*)\\s*[=:]\\s*[\"']?([^\"'\\s,;}{][^\"'\\r\\n]*)");

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)(xxx+|\\$\\{[^}]*}|\\$\\{env\\.[^}]*}|\\*{3,}|<[^>]+>|changeme|your\\w*|dummy|todo)");

    @Override
    public String name() {
        return "secret";
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    @Override
    protected void checkLine(String relativeFile, String line, int lineNo, List<CheckIssue> sink) {
        Matcher m = ASSIGNMENT.matcher(line);
        while (m.find()) {
            String value = m.group(2).trim();
            if (value.isEmpty() || isPlaceholder(value)) {
                continue;
            }
            String keyName = m.group(1);
            sink.add(CheckIssue.at(relativeFile, lineNo, m.start(2) + 1, name(),
                    Severity.ERROR, "疑似硬编码密钥: " + keyName + " = " + mask(value)));
        }
    }

    /** 判断值是否为占位符或示例值。 */
    private static boolean isPlaceholder(String value) {
        return PLACEHOLDER.matcher(value).matches();
    }

    /** 对值打码显示，避免把真实密钥写入报告。 */
    private static String mask(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
