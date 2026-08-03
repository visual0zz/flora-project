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
 * 对每一处赋值 {@code key = value} 同时考察键名与值内容两种形态：
 * <ul>
 *   <li>键名像密钥（password / secret / token / apiKey 等）→ 报告 <b>WARNING</b>，
 *       提示可能存在硬编码凭据，但不阻断构建；</li>
 *   <li>值内容像真实密钥/令牌（高熵串或典型前缀如 {@code sk-}、{@code AKIA}、{@code ghp_}、
 *       JWT 等）→ 报告 <b>ERROR</b>，高置信度判为泄露；</li>
 *   <li>值内容像测试/ mock 假数据（{@code xxxx}、{@code <your-key>}、{@code example}、
 *       {@code changeme} 等）→ 整体豁免，不报告。</li>
 * </ul>
 * 综合优先级：值像 mock 数据优先（整体不报）；否则值像真实密钥报 ERROR；
 * 否则键名像密钥报 WARNING。值一律打码，避免把真实密钥写进报告。
 */
public final class SecretCheck extends LineCheck {

    private static final Set<String> EXTENSIONS = Set.of(
            ".java", ".properties", ".yaml", ".yml", ".json", ".xml", ".kts", ".gradle");

    /** 键名像密钥：形如 xxxPassword / apiKey / accessKey / clientSecret 等。 */
    private static final Pattern KEY_NAME = Pattern.compile(
            "(?i)\\b(?:password|passwd|pwd|secret|token|apikey|access[_-]?key|secret[_-]?key|"
                    + "private[_-]?key|auth[_-]?token|api[_-]?secret|client[_-]?secret|credential)\\b");

    /** 捕获形如 {@code key = value} / {@code key: value} 的赋值（键为普通标识符）。 */
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "([A-Za-z_$][A-Za-z0-9_.-]*)\\s*[=:]\\s*([^\\r\\n]*)");

    /** 值像真实密钥：常见厂商前缀或高熵串（长且字母数字混合）。 */
    private static final Pattern SECRET_LIKE = Pattern.compile(
            "(?i)"
                    + "(?:sk|pk|rk)-[A-Za-z0-9]"              // Stripe
                    + "|AKIA[0-9A-Z]{8,}"                      // AWS access key id
                    + "|ASIA[0-9A-Z]{8,}"                      // AWS临时密钥
                    + "|gh[pousr]_[A-Za-z0-9]{16,}"            // GitHub token
                    + "|glpat-[A-Za-z0-9_-]{16,}"              // GitLab personal token
                    + "|AIza[0-9A-Za-z_-]{30,}"                // Google API key
                    + "|ya29\\.[0-9A-Za-z_-]+"                 // Google OAuth
                    + "|xox[baprs]-[0-9A-Za-z-]+"              // Slack token
                    + "|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+" // JWT
                    + "|-----BEGIN[A-Z ]*PRIVATE KEY-----"      // PEM 私钥
                    + "|(?:Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+" // 鉴权头
                    + "|[A-Za-z0-9+/=_-]{16,}");               // 通用高熵串

    /** 值像测试/mock 假数据：占位符、示例、明显的假值。 */
    private static final Pattern MOCK_LIKE = Pattern.compile(
            "(?i)"
                    + "x{3,}"                                  // xxxx
                    + "|\\*{3,}"                               // ****
                    + "|<[^>]*>"                               // <your-key>
                    + "|\\$\\{[^}]*}"                          // ${...}
                    + "|changeme|change-me"
                    + "|your[-_]?\\w*"
                    + "|\\b(test|example|sample|dummy|fake|mock|placeholder|null|none|empty|todo|tbd|fixme)\\b"
                    + "|\\b0{4,}\\b|123456|000000|111111"
                    + "|localhost|example\\.com|127\\.0\\.0\\.1");

    /** UUID 形态（8-4-4-4-12 十六进制），高熵但属普通标识符，不应判为密钥。 */
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

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
            String key = m.group(1);
            String value = stripValue(m.group(2));
            if (value.isEmpty() || looksLikeMock(value)) {
                continue; // 假数据/占位符整体豁免
            }
            boolean keyLikeSecret = KEY_NAME.matcher(key).find();
            boolean valueLikeSecret = looksLikeSecret(value);
            if (valueLikeSecret) {
                sink.add(CheckIssue.at(relativeFile, lineNo, m.start(2) + 1, name(),
                        Severity.ERROR, "疑似硬编码密钥(值形态): " + key + " = " + mask(value)));
            } else if (keyLikeSecret) {
                sink.add(CheckIssue.at(relativeFile, lineNo, m.start(2) + 1, name(),
                        Severity.WARNING, "疑似硬编码密钥(键名): " + key + " = " + mask(value)));
            }
        }
    }

    /** 去掉赋值值的引号与结尾分隔符/括号，得到纯净值用于判定。 */
    private static String stripValue(String raw) {
        String s = raw.trim();
        // 成对的引号优先整体剥离（如 "value" 或 'value'）
        if ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).trim();
        } else if (s.startsWith("\"") || s.startsWith("'")) {
            s = s.substring(1).trim();
        }
        // 剥离结尾可能存在的分隔符、括号与空白（含 "value"; } 这类情形）
        s = s.replaceAll("[\\s\"',;)}]+$", "");
        return s;
    }

    /** 值内容是否像真实密钥（厂商前缀或高熵串）。 */
    private static boolean looksLikeSecret(String value) {
        if (!SECRET_LIKE.matcher(value).find()) {
            return false;
        }
        // 高熵分支需同时含字母与数字，避免把普通长单词误判为密钥；
        // 形如 UUID 的常规标识符即使满足高熵也不视为密钥
        return value.length() >= 16 && hasLetter(value) && hasDigit(value)
                && !UUID.matcher(value).matches();
    }

    /** 值内容是否像测试/mock 假数据。 */
    private static boolean looksLikeMock(String value) {
        return MOCK_LIKE.matcher(value).find();
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** 对值打码显示，避免把真实密钥写入报告。 */
    private static String mask(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
