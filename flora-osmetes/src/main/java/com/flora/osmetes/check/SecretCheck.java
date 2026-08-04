package com.flora.osmetes.check;

import com.flora.entropy.mesure.Entropy;
import com.flora.osmetes.CheckIssue;
import com.flora.osmetes.Severity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 密钥检查项：扫描文本文件中疑似硬编码的密钥、口令、令牌等敏感配置。
 * <p>
 * 只有<b>字面量右值</b>参与判定：加引号的串一律算字面量，未加引号的串仅在
 * {@code .properties} / {@code .yaml} / {@code .yml} 中算（这些格式允许裸标量）；
 * 纯数值一律排除。因此 {@code this.password = password}、{@code SECRET_LEN = 32}、
 * {@code TOKEN_COLORS = new HashMap<>()} 这类变量引用与表达式不会被报告。
 * <p>
 * 对每一处字面量赋值 {@code key = value} 同时考察键名与值内容两种形态：
 * <ul>
 *   <li>键名像密钥（password / secret / token / apiKey 等，含 {@code _} {@code -} {@code .} 分隔的
 *       复合写法如 {@code db_password}、{@code auth-token}）→ 报告 <b>WARNING</b>；</li>
 *   <li>值内容带典型厂商前缀（{@code sk-}、{@code AKIA}、{@code ghp_}、JWT 等）→ 高置信度，
 *       报告 <b>ERROR</b>；</li>
 *   <li>值内容无前缀但形似随机密钥（长且经 {@link Entropy} 评估为高熵、字符类别混合）→
 *       报告 <b>ERROR</b>；</li>
 *   <li>值是占位符/示例/假数据（{@code <your-key>}、{@code ${...}}、{@code example}、
 *       {@code xxxx}、{@code ****} 等）→ 整体豁免，不报告；</li>
 *   <li>值是常规公开结构（UUID、时间戳、十六进制摘要、URL、IPv4/IPv6、语义版本、
 *       算法名、路径、FQDN 等，见 {@link #KNOWN_FORMATS}）→ 按<b>结构</b>精确豁免值形态
 *       判定（不报 ERROR），但若键名像密钥仍给 WARNING 兜底。</li>
 * </ul>
 * 综合优先级：占位符整体豁免最优先；其次厂商前缀判 ERROR；其次常规结构按结构豁免值形态；
 * 否则值像随机密钥报 ERROR；否则键名像密钥报 WARNING。值一律打码，避免把真实密钥写进报告。
 * <p>
 * 通用配置（经 {@link #configure(Map)} 传入）可调整阈值：阈值越低越激进（更易报），
 * 越高越保守。支持的键：{@code secret.minLength}、{@code secret.minClasses}、
 * {@code secret.minEntropy}。
 */
public final class SecretCheck extends LineCheck {

    private static final Set<String> EXTENSIONS = Set.of(
            ".java", ".properties", ".yaml", ".yml", ".json", ".xml", ".kts", ".gradle");

    /** 键名像密钥：形如 xxxPassword / apiKey / accessKey / clientSecret 等。 */
    private static final Pattern KEY_NAME = Pattern.compile(
            "(?i)(?<![[A-Za-z0-9]])"
                    + "(?:password|passwd|pwd|secret|token|api[_-]?key|apikey|"
                    + "access[_-]?key|secret[_-]?key|private[_-]?key|auth[_-]?token|"
                    + "api[_-]?secret|client[_-]?secret|credential)"
                    + "(?![[A-Za-z0-9]])");

    /**
     * 捕获形如 {@code key = value} / {@code key: value} 的赋值（键为普通标识符）。
     * <p>值仅取紧跟 {@code =}/{}:{@code } 的<b>第一个引号字面量</b>（{@code "..."} 或 {@code '...'}）
     * 或<b>首个裸 token</b>（遇空白、{@code ; , ) } =} 即止），不贪心吞到行尾，
     * 避免把 {@code a = "x" b = "y"} 或 {@code a = f(b.c)} 的后续内容误并入值。</p>
     */
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "([A-Za-z_$][A-Za-z0-9_.-]*)\\s*[=:]\\s*"
                    + "(\"(?:[^\"]*)\"|'(?:[^']*)'|[^\\s;,)}=]+)");

    /** 值带典型厂商前缀或已知结构 → 高置信度判为真实密钥。 */
    private static final Pattern SECRET_PREFIX = Pattern.compile(
            "(?i)"
                    + "(?:sk|pk|rk)-[A-Za-z0-9]"                  // Stripe
                    + "|AKIA[0-9A-Z]{8,}"                          // AWS access key id
                    + "|ASIA[0-9A-Z]{8,}"                          // AWS 临时密钥
                    + "|gh[pousr]_[A-Za-z0-9]{16,}"                // GitHub token
                    + "|glpat-[A-Za-z0-9_-]{16,}"                  // GitLab personal token
                    + "|AIza[0-9A-Za-z_-]{30,}"                    // Google API key
                    + "|ya29\\.[0-9A-Za-z_-]+"                     // Google OAuth
                    + "|xox[baprs]-[0-9A-Za-z-]+"                  // Slack token
                    + "|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+" // JWT
                    + "|-----BEGIN[A-Z ]*PRIVATE KEY-----"          // PEM 私钥
                    + "|(?:Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+"   // 鉴权头
                    + "|npm_[A-Za-z0-9_-]{20,}"                    // npm token
                    + "|dckr_[A-Za-z0-9_-]{20,}"                    // Docker token
                    + "|AC[0-9a-fA-F]{24,}"                        // Twilio account sid
                    + "|SK[0-9a-fA-F]{24,}"                        // Twilio API key
                    // URL 内嵌凭据 scheme://user:pass@host —— 与长度/熵无关的确定性泄露
                    + "|[A-Za-z][A-Za-z0-9+.-]*://[^/\\s:@]+:[^/\\s:@]+@");

    /** 通用候选：够长且由字母/数字/常见分隔符组成，再由熵与字符类别进一步判定。 */
    private static final Pattern GENERIC_ALNUM = Pattern.compile(
            "[A-Za-z0-9+/=_-]{16,}");

    /**
     * 值是显式的占位符 / 示例 / 假数据 → <b>整体豁免</b>，键名与值形态都不报。
     * <p>与 {@link #KNOWN_FORMATS} 的区别：这里的值<b>压根不是真数据</b>，
     * 提示开发者"这里像密钥"毫无价值；而常规结构豁免清单里的值是真实数据，
     * 只是结构上不构成凭据，故仍保留键名 WARNING。</p>
     */
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)"
                    + "<[^>]*>"                                    // <your-key>
                    + "|\\$\\{[^}]*}"                              // ${...}
                    + "|\\$\\([^)]*\\)"                            // $(...)
                    + "|%[A-Za-z_][A-Za-z0-9_]*%"                  // %ENV_VAR%
                    + "|\\{\\{[^}]*}}"                             // {{ template }}
                    + "|changeme|change-me|replace-?me"
                    + "|your[-_]?\\w*|my[-_]?(?:secret|password|token|key)\\w*"
                    + "|\\b(?:test|example|sample|demo|dummy|fake|mock|stub|fixture|"
                    + "placeholder|redacted|omitted|null|none|nil|empty|todo|tbd|fixme|"
                    + "unset|undefined|default)\\b"
                    // 仅列公认的"弱口令样例"；不含 admin/root 等常见真实子串，
                    // 否则任何含这些词的值都会被整体豁免，是隐性漏报。
                    + "|\\b(?:123456|1234567890|abcdef|qwerty|letmein|foobar|passw0rd)\\b"
                    + "|localhost|example\\.(?:com|org|net)|127\\.0\\.0\\.1|0\\.0\\.0\\.0");

    /**
     * 常规数据结构豁免清单：这些形态天然"长且字符混合"，在熵评估上与随机密钥无法区分，
 * 但它们都是<b>有确定语法的公开数据</b>，不构成凭据泄露。
 * <p>
 * 每条规则按<b>整值完全匹配</b>生效（{@link Matcher#matches()}），避免"值里含一个
     * 日期子串"就把整条密钥豁免掉。本清单只豁免<b>值形态</b>（ERROR）判定；若键名本身
     * 像密钥，仍会给出 WARNING 作为兜底——例如 {@code secretKey = "<64位hex>"} 无法排除
     * 它是十六进制编码的真密钥，此时值形态不报但键名仍提示复核。
     */
    private static final List<KnownFormat> KNOWN_FORMATS = List.of(
            // ── 时间与日期 ──
            new KnownFormat("iso-datetime",
                    "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:?\\d{2}(?::?\\d{2})?"
                            + "(?:[.,]\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?"),
            new KnownFormat("compact-datetime", "\\d{8}T\\d{6}(?:\\.\\d+)?Z?"),
            new KnownFormat("date", "\\d{4}[-/]\\d{2}[-/]\\d{2}|\\d{2}[-/]\\d{2}[-/]\\d{4}"),
            new KnownFormat("time", "\\d{2}:\\d{2}(?::\\d{2})?(?:[.,]\\d{1,9})?"),
            new KnownFormat("iso-duration",
                    "P(?:\\d+[YMWD])*(?:T(?:\\d+(?:\\.\\d+)?[HMS])+)?"),
            new KnownFormat("epoch-millis", "\\d{10,13}"),

            // ── 标识符 ──
            new KnownFormat("uuid",
                    "\\{?[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}}?"),
            // 纯十六进制串：git sha、MD5/SHA 摘要、校验和。也可能是 hex 编码的密钥，
            // 故仅豁免值形态判定，键名像密钥时仍报 WARNING。
            new KnownFormat("hex-string", "(?:0[xX])?[0-9a-fA-F]{7,}"),
            new KnownFormat("sri-integrity",
                    "(?:sha256|sha384|sha512)-[A-Za-z0-9+/]+={0,2}"),

            // ── 版本与坐标 ──
            new KnownFormat("semver",
                    "v?\\d+\\.\\d+(?:\\.\\d+)*(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?"),
            new KnownFormat("maven-coordinate", "[\\w.-]+:[\\w.-]+(?::[\\w.-]+){1,3}"),

            // ── 网络 ──
            // 排除内嵌凭据的 URL（形如 scheme://user:pass@host），那种情况是真泄露。
            new KnownFormat("url",
                    "(?!\\w+://[^/\\s@]*:[^/\\s@]*@)[A-Za-z][A-Za-z0-9+.-]*://\\S*"),
            new KnownFormat("ipv4", "(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?(?:/\\d{1,2})?"),
            new KnownFormat("ipv6", "(?:[0-9A-Fa-f]{0,4}:){2,7}[0-9A-Fa-f]{0,4}(?:%\\w+)?"),
            new KnownFormat("mac-address", "(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}"),
            new KnownFormat("mime-type",
                    "[a-z]+/[a-zA-Z0-9.+-]+(?:\\s*;\\s*\\w+=[\\w\"'-]+)*"),

            // ── 代码与文件系统 ──
            new KnownFormat("qualified-name",
                    "(?:[A-Za-z_$][\\w$]*\\.){2,}[A-Za-z_$][\\w$]*"),
            new KnownFormat("unix-path", "(?:/[\\w.@+-]+)+/?"),
            new KnownFormat("windows-path", "[A-Za-z]:[\\\\/][^\\s]*"),
            new KnownFormat("classpath-resource", "(?:[\\w.-]+/)+[\\w.-]+\\.[A-Za-z0-9]+"),

            // ── 密码学算法名（JCA transformation 等，是算法标识不是密钥材料）──
            // 词表含 pbe（Password-Based Encryption，如 PBEWithMD5AndDES、PBEWithHmacSHA256），
            // 否则 PBE 系列算法名会漏到熵判定被误报为随机密钥。
            new KnownFormat("crypto-algorithm",
                    "(?i)(?:pbkdf2|pbe|pbes|hmac|aes|des|3des|rsa|dsa|ecdsa|ecdh|ec|sha\\d*|md5|"
                            + "blowfish|rc4|chacha20|poly1305|argon2\\w*|scrypt|bcrypt|"
                            + "base64|utf|iso|pkcs|x509)"
                            + "[a-z0-9]*(?:with[a-z0-9]+)*(?:[/_-][a-z0-9]+)*"),

            // ── 其他常规结构 ──
            new KnownFormat("locale", "[a-z]{2,3}(?:[_-][A-Za-z]{2,4}){1,2}"),
            new KnownFormat("color-hex", "#[0-9a-fA-F]{3,8}"),
            new KnownFormat("cron", "[\\d*?/,\\-LW#]+(?:\\s+[\\d*?/,\\-LW#]+){4,6}"),
            new KnownFormat("number-list", "\\d+(?:[,;|]\\d+)+"),
            // 自然语言短语：多个单词（含常见标点、数字）按常规语序组合，不是随机密钥。
            // 单词 token 允许内嵌数字与连字符（如 pbeWithMD2AndDES-CBC unsupported 这种
            // "算法标识 + 描述词"短语），避免把算法名当成高熵随机密钥。
            new KnownFormat("natural-language",
                    "[A-Za-z0-9][A-Za-z0-9'\\-.]*(?:[\\s,.;:!?]+[A-Za-z0-9][A-Za-z0-9'\\-.]*){1,12}"));

    /**
     * 值可以是裸标量（不加引号）的配置格式。
     * <p>这些格式里 {@code password: s3cr3t} 是合法且常见的真实写法；其余格式（源码、
     * JSON、XML）中密钥必然出现在引号内，未加引号的右值只可能是变量、数字或表达式。</p>
     */
    private static final Set<String> BARE_SCALAR_EXTENSIONS = Set.of(
            ".properties", ".yaml", ".yml");

    /** 纯数值右值（含长度常量、超时毫秒数等），任何格式下都不是密钥。 */
    private static final Pattern NUMERIC = Pattern.compile(
            "[+-]?(?:0[xXbB])?[0-9A-Fa-f_]+(?:\\.[0-9_]+)?[LlFfDd]?");

    /** 通用候选被判定为密钥的最小长度。 */
    private int minLength = 16;
    /** 通用候选被判定为密钥所需的最少字母/数字类别（小写/大写/数字）数。 */
    private int minClasses = 3;
    /** 通用候选被判定为密钥所需的最小归一化熵（{@code [0,1]}）。 */
    private double minEntropy = 0.5;

    @Override
    public String name() {
        return "secret";
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    @Override
    public void configure(Map<String, String> properties) {
        applyInt(properties, "secret.minLength", v -> minLength = v);
        applyInt(properties, "secret.minClasses", v -> minClasses = v);
        applyDouble(properties, "secret.minEntropy", v -> minEntropy = v);
    }

    @Override
    protected void checkLine(String relativeFile, String line, int lineNo, List<CheckIssue> sink) {
        Matcher m = ASSIGNMENT.matcher(line);
        while (m.find()) {
            String key = m.group(1);
            String raw = m.group(2);
            String value = stripValue(raw);
            if (value.isEmpty() || looksLikePlaceholder(value)) {
                continue; // 占位符/示例/假数据整体豁免
            }
            // 只有"字面量右值"才可能是硬编码密钥；变量引用、数字、表达式一律跳过。
            boolean literal = isLiteralValue(raw, relativeFile);
            boolean keyLikeSecret = literal && KEY_NAME.matcher(key).find();
            boolean valueLikeSecret = looksLikeSecret(value, literal);
            if (valueLikeSecret) {
                sink.add(CheckIssue.at(relativeFile, lineNo, m.start(2) + 1, name(),
                        Severity.ERROR, "疑似硬编码密钥(值形态): " + key + " = " + mask(value)));
            } else if (keyLikeSecret) {
                sink.add(CheckIssue.at(relativeFile, lineNo, m.start(2) + 1, name(),
                        Severity.WARNING, "疑似硬编码密钥(键名): " + key + " = " + mask(value)));
            }
        }
    }

    /**
     * 右值是否为可能承载密钥的字面量。
     * <p>加引号的串一律算字面量；未加引号的串仅在 {@code .properties} / {@code .yaml} /
     * {@code .yml} 中算字面量（这些格式允许裸标量）。其他格式里未加引号的右值只会是
     * 变量引用或表达式（如 {@code this.password = password}、{@code X = new HashMap<>()}），
     * 不构成硬编码。纯数字无论何种格式都不视为密钥。</p>
     */
    private static boolean isLiteralValue(String raw, String relativeFile) {
        String s = raw.trim();
        if (NUMERIC.matcher(s).matches()) {
            return false;
        }
        // 含换行转义的串是拼接出来的多行文本（YAML 块、MANIFEST 片段等），
        // 不是单一密钥字面量；整体判熵会把多行内容混在一起，必然失真。
        if (s.contains("\\n") || s.contains("\\r")) {
            return false;
        }
        if (s.startsWith("\"") || s.startsWith("'")) {
            return true;
        }
        int dot = relativeFile.lastIndexOf('.');
        return dot >= 0
                && BARE_SCALAR_EXTENSIONS.contains(relativeFile.substring(dot).toLowerCase());
    }

    /** 去掉赋值值的引号与结尾分隔符/括号，得到纯净值用于判定。 */
    private static String stripValue(String raw) {
        String s = raw.trim();
        // 成对的引号优先整体剥离（如 "value" 或 'value'）
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        } else {
            // 仅单侧有引号时只剥该侧，避免把对侧内容误当配对引号
            if (s.startsWith("\"") || s.startsWith("'")) {
                s = s.substring(1).trim();
            }
            if (s.endsWith("\"") || s.endsWith("'")) {
                s = s.substring(0, s.length() - 1).trim();
            }
        }
        // 剥离结尾可能存在的分隔符、括号与空白（含 "value"; } 这类情形）
        s = s.replaceAll("[\\s\"',;)}]+$", "");
        return s;
    }

    /**
     * 值内容是否像真实密钥：仅对字面量右值判定。带典型厂商前缀直接判为密钥
     * （前缀是高置信信号，即便整值形如 URL 也照报——URL 里带 token 本身就是泄露）；
     * 否则先过 {@link #KNOWN_FORMATS} 结构豁免，再按"够长 + 字符类别混合 +
     * 经 {@link Entropy} 评估为高熵"判定。
     */
    private boolean looksLikeSecret(String value, boolean literal) {
        if (!literal) {
            return false;
        }
        if (SECRET_PREFIX.matcher(value).find()) {
            return true;
        }
        if (matchedKnownFormat(value) != null) {
            return false;
        }
        if (!GENERIC_ALNUM.matcher(value).find()) {
            return false;
        }
        if (value.length() < minLength) {
            return false;
        }
        if (Entropy.alnumClasses(value) < minClasses) {
            return false;
        }
        return Entropy.normalized(value) >= minEntropy;
    }

    /**
     * 值命中的常规结构名称，未命中返回 {@code null}。
     * <p>按整值完全匹配，命中即说明该值是有确定语法的公开数据而非凭据。</p>
     */
    private static String matchedKnownFormat(String value) {
        if (hasLongSequentialRun(value)) {
            return "alphabet";
        }
        for (KnownFormat format : KNOWN_FORMATS) {
            if (format.pattern().matcher(value).matches()) {
                return format.name();
            }
        }
        return null;
    }

    /**
     * 值中是否含足够长的连续递增字符段（{@code abcdefghij}、{@code 0123456789}）。
     * <p>字母表常量（如随机串生成器的字符池）天然又长又混合字符类别，熵评估无法与随机
     * 密钥区分，但"字符严格递增"是它独有的结构特征，可据此识别。</p>
     */
    private static boolean hasLongSequentialRun(String value) {
        final int required = 10;
        int run = 1;
        for (int i = 1; i < value.length(); i++) {
            run = value.charAt(i) == value.charAt(i - 1) + 1 ? run + 1 : 1;
            if (run >= required) {
                return true;
            }
        }
        return false;
    }

    /** 值内容是否为占位符/示例/假数据。 */
    private static boolean looksLikePlaceholder(String value) {
        return PLACEHOLDER.matcher(value).find() || isMasked(value);
    }

    /**
     * 值是否为打码串或单调重复串（{@code xxxx}、{@code ****}、{@code 0000} 等）。
     * <p>用"单一字符占比过半"判定，而非匹配 {@code x{3,}} 之类的固定模式——后者会把
     * 恰好含连续三个 {@code x} 的真实 base64 密钥整条豁免掉，是隐性漏报。</p>
     */
    private static boolean isMasked(String value) {
        if (value.length() < 4) {
            return false;
        }
        Map<Integer, Integer> freq = new HashMap<>();
        value.codePoints().forEach(cp -> freq.merge(cp, 1, Integer::sum));
        int max = freq.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return max * 2 >= value.length();
    }

    /** 从配置解析整数阈值；非法值忽略，保留默认。 */
    private static void applyInt(Map<String, String> properties, String key, IntConsumer set) {
        String v = properties.get(key);
        if (v == null) {
            return;
        }
        try {
            int parsed = Integer.parseInt(v.trim());
            if (parsed >= 0) {
                set.accept(parsed);
            }
        } catch (NumberFormatException ignored) {
            // 配置非法时沿用默认阈值
        }
    }

    /** 从配置解析浮点阈值；非法值忽略，保留默认。 */
    private static void applyDouble(Map<String, String> properties, String key, DoubleConsumer set) {
        String v = properties.get(key);
        if (v == null) {
            return;
        }
        try {
            double parsed = Double.parseDouble(v.trim());
            if (parsed >= 0.0 && parsed <= 1.0) {
                set.accept(parsed);
            }
        } catch (NumberFormatException ignored) {
            // 配置非法时沿用默认阈值
        }
    }

    /** 对值打码显示，避免把真实密钥写入报告。 */
    private static String mask(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    /** 常规数据结构豁免项：名称便于调试与报告，pattern 用于整值匹配判定。 */
    private record KnownFormat(String name, Pattern pattern) {

        KnownFormat(String name, String regex) {
            this(name, Pattern.compile(regex));
        }
    }
}
