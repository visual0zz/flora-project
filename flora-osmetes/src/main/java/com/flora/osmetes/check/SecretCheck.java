package com.flora.osmetes.check;

import com.flora.entropy.mesure.Entropy;
import com.flora.osmetes.CheckIssue;
import com.flora.osmetes.Severity;

import java.util.ArrayList;
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
 * 候选为<b>字符串字面量</b>（所有文件，含引号内容）与<b>裸标量</b>（仅
 * {@code .properties} / {@code .yaml} / {@code .yml} / {@code .env} 等允许裸值的格式，
 * 取 {@code =}/{@code :} 后的第一个无引号 token）。纯数值、注释行与未闭合引号不参与。
 * <p>
 * 对每个候选只考察<b>值内容</b>形态，不依赖赋值键名：
 * <ul>
 *   <li>值内容带典型厂商前缀（{@code sk-}、{@code AKIA}、{@code ghp_}、JWT 等）→ 高置信度，
 *       报告 <b>ERROR</b>；</li>
 *   <li>值是占位符/示例/假数据（{@code <your-key>}、{@code ${...}}、{@code example}、
 *       {@code xxxx}、{@code ****} 等）→ 整体豁免，不报告；</li>
 *   <li>值含非密钥语法符号（正则元字符、格式符、模板、路径、分隔符等，见
 *       {@link #NON_SECRET_SYNTAX}）或属常规公开结构（UUID、时间戳、十六进制摘要、URL、
 *       IPv4/IPv6、语义版本、算法名、路径、FQDN 等，见 {@link #KNOWN_FORMATS}）→ 按结构豁免；</li>
 *   <li>其余值按长度 + 字符类别混合 + 香农熵评估，形似随机密钥 → 报告 <b>ERROR</b>。</li>
 * </ul>
 * 综合优先级：占位符整体豁免最优先；其次厂商前缀判 ERROR；其次语法/结构豁免；
 * 否则值像随机密钥报 ERROR。值一律打码，避免把真实密钥写进报告。
 * <p>
 * 通用配置（经 {@link #configure(Map)} 传入）可调整阈值：阈值越低越激进（更易报），
 * 越高越保守。支持的子键（已剥离 {@code secret.} 前缀）：{@code minLength}、
 * {@code minClasses}、{@code minEntropy}；
 * 用户侧配置名分别为 {@code secret.minLength} 等。
 */
public final class SecretCheck extends LineCheck {

    private static final Set<String> EXTENSIONS = Set.of(
            ".java", ".properties", ".yaml", ".yml", ".json", ".xml", ".kts", ".gradle", ".env");

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

    /**
     * 非密钥语法符号：密钥字符集（字母数字 + {@code - _ . / + =}）之外、
     * 但大量出现在正则/格式串/模板/SQL/HTML/路径/分隔列表中的符号。
     * <p>值含任一符号即结构上不可能是随机密钥——base64 / hex / UUID / slug 均不含这些
     * 符号（base64 的 {@code + / =} 与 base64url 的 {@code - _} 不在此列），
     * 故可按此将正则字面量、格式串、模板、路径与各类分隔结构安全排除。</p>
     * <p>含中文（CJK 统一表意文字）的串亦在此列：中文异常消息、日志文案、注释性文本
     * 不是随机密钥，含任一汉字即整体豁免，避免正常中文串被熵判定误报。</p>
     */
    private static final Pattern NON_SECRET_SYNTAX = Pattern.compile(
            "[\\\\\\[\\]()|^$*?%{}<>&;:,'\u4E00-\u9FFF]");

    /** 通用候选：够长且由字母/数字/常见分隔符组成，再由熵与字符类别进一步判定。 */
    private static final Pattern GENERIC_ALNUM = Pattern.compile(
            "[A-Za-z0-9+/=_-]{16,}");

    /**
     * 值是显式的占位符 / 示例 / 假数据 → <b>整体豁免</b>，键名与值形态都不报。
     * <p>与 {@link #KNOWN_FORMATS} 的区别：这里的值<b>压根不是真数据</b>，
     * 提示开发者"这里像密钥"毫无价值；而常规结构豁免清单里的值是真实数据，
     * 只是结构上不构成凭据。</p>
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
     * 日期子串"就把整条密钥豁免掉。
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
            // 此处按结构豁免——hex 编码的密钥无法与摘要区分，交由人工复核。
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
            new KnownFormat("crypto-algorithm",
                    "(?i)(?:pbkdf2|pbe|pbes|hmac|aes|des|3des|rsa|dsa|ecdsa|ecdh|ec|sha\\d*|md5|"
                            + "blowfish|rc4|chacha20|poly1305|argon2\\w*|scrypt|bcrypt|"
                            + "base64|utf|iso|pkcs|x509)"
                            + "[a-z0-9]*(?:with[a-z0-9]+)*(?:[/_-][a-z0-9]+)*"),
            new KnownFormat("crypto-algorithm-message",
                    "(?i)(?:pbkdf2|hmac|aes|des|3des|rsa|dsa|ecdsa|ecdh|ec|sha\\d*|md5|"
                            + "blowfish|rc4|chacha20|poly1305|argon2\\w*|scrypt|bcrypt|"
                            + "base64|utf|iso|pkcs|x509|pbe\\w+)"
                            + "[a-z0-9]*(?:with[a-z0-9]+)*(?:[/_-][a-z0-9]+)*"
                            + "(?:\\s+\\w+)*"),

            // ── 其他常规结构 ──
            new KnownFormat("locale", "[a-z]{2,3}(?:[_-][A-Za-z]{2,4}){1,2}"),
            new KnownFormat("color-hex", "#[0-9a-fA-F]{3,8}"),
            new KnownFormat("cron", "[\\d*?/,\\-LW#]+(?:\\s+[\\d*?/,\\-LW#]+){4,6}"),
            new KnownFormat("number-list", "\\d+(?:[,;|]\\d+)+"),
            // 自然语言短语：多个单词（含常见标点、数字）按常规语序组合，不是随机密钥。
            new KnownFormat("natural-language",
                    "[A-Za-z0-9][A-Za-z0-9'\\-.]*(?:[\\s,.;:!?]+[A-Za-z0-9][A-Za-z0-9'\\-.]*){1,12}"));

    /** 值可以是裸标量（不加引号）的配置格式。 */
    private static final Set<String> BARE_SCALAR_EXTENSIONS = Set.of(
            ".properties", ".yaml", ".yml", ".env");

    /** 纯数值（含长度常量、超时毫秒数等），任何格式下都不是密钥。 */
    private static final Pattern NUMERIC = Pattern.compile(
            "[+-]?(?:0[xXbB])?[0-9A-Fa-f_]+(?:\\.[0-9_]+)?[LlFfDd]?");

    /** 通用候选被判定为密钥的最小长度。 */
    private int minLength = 16;
    /** 候选被判定为密钥所需的最少字母/数字类别（小写/大写/数字）数。 */
    private int minClasses = 3;
    /** 候选被判定为密钥所需的最小归一化熵（{@code [0,1]}）。 */
    private double minEntropy = 0.5;

    @Override
    public String name() {
        return "secret";
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    /** 通用配置子键（已剥离 {@code secret.} 前缀）：判定为密钥的最小长度。 */
    static final String CONFIG_MIN_LENGTH = "minLength";
    /** 通用配置子键（已剥离 {@code secret.} 前缀）：所需的最少字符类别数。 */
    static final String CONFIG_MIN_CLASSES = "minClasses";
    /** 通用配置子键（已剥离 {@code secret.} 前缀）：所需的最小归一化熵。 */
    static final String CONFIG_MIN_ENTROPY = "minEntropy";

    @Override
    public void configure(Map<String, String> properties) {
        applyInt(properties, CONFIG_MIN_LENGTH, v -> minLength = v);
        applyInt(properties, CONFIG_MIN_CLASSES, v -> minClasses = v);
        applyDouble(properties, CONFIG_MIN_ENTROPY, v -> minEntropy = v);
    }

    @Override
    protected void checkLine(String relativeFile, String line, int lineNo, List<CheckIssue> sink) {
        // 字符串字面量（所有文件）
        for (Candidate c : extractStringLiterals(line)) {
            examine(c, relativeFile, lineNo, sink);
        }
        // 裸标量（仅允许裸值的配置格式）
        if (isBareScalarFile(relativeFile)) {
            for (Candidate c : extractBareScalars(line)) {
                examine(c, relativeFile, lineNo, sink);
            }
        }
    }

    /** 对单个候选值执行判定链，命中则报告。 */
    private void examine(Candidate c, String relativeFile, int lineNo, List<CheckIssue> sink) {
        String value = c.value();
        if (value.isEmpty() || looksLikePlaceholder(value)) {
            return; // 占位符/示例/假数据整体豁免
        }
        if (SECRET_PREFIX.matcher(value).find()) {
            sink.add(CheckIssue.at(relativeFile, lineNo, c.column(), name(),
                    Severity.ERROR, "疑似硬编码密钥(前缀形态): " + mask(value)));
            return;
        }
        if (NON_SECRET_SYNTAX.matcher(value).find()) {
            return; // 含正则/格式符/模板/路径/分隔符号，结构上不可能是密钥
        }
        if (matchedKnownFormat(value) != null) {
            return; // 常规公开结构，按结构豁免
        }
        if (looksLikeSecret(value)) {
            sink.add(CheckIssue.at(relativeFile, lineNo, c.column(), name(),
                    Severity.ERROR, "疑似硬编码密钥(值形态): " + mask(value)));
        }
    }

    /**
     * 提取行内所有字符串字面量内容：去掉外层引号，保留内部原文（含转义序列）。
     * <p>未闭合的引号（注释、残缺代码）不产出候选。</p>
     */
    private static List<Candidate> extractStringLiterals(String line) {
        List<Candidate> out = new ArrayList<>();
        int n = line.length();
        int i = 0;
        while (i < n) {
            char c = line.charAt(i);
            if (c != '"' && c != '\'') {
                i++;
                continue;
            }
            char quote = c;
            int start = i;
            i++;
            StringBuilder sb = new StringBuilder();
            boolean closed = false;
            while (i < n) {
                char ch = line.charAt(i);
                if (ch == '\\' && i + 1 < n) {
                    sb.append(ch).append(line.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (ch == quote) {
                    closed = true;
                    break;
                }
                sb.append(ch);
                i++;
            }
            if (closed) {
                out.add(new Candidate(sb.toString(), start + 1));
                i++; // 跳过闭引号
            } else {
                i = start + 1; // 未闭合，从引号后继续扫描（可能是注释/残缺代码）
            }
        }
        return out;
    }

    /**
     * 提取裸标量：{@code =}/{@code :} 之后的第一个无引号 token（到空白或分隔符为止）。
     * <p>仅用于允许裸值的配置格式；注释行、引号值（交给字符串提取）与纯数字跳过。</p>
     */
    private static List<Candidate> extractBareScalars(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")
                || trimmed.startsWith("//")) {
            return List.of();
        }
        int sep = -1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '=' || c == ':') {
                sep = i;
                break;
            }
        }
        if (sep < 0) {
            return List.of();
        }
        int i = sep + 1;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        if (i >= line.length()) {
            return List.of();
        }
        char first = line.charAt(i);
        if (first == '"' || first == '\'') {
            return List.of(); // 引号值由字符串提取处理，避免重复候选
        }
        int start = i;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c) || c == ';' || c == ',' || c == ')' || c == '}' || c == ']') {
                break;
            }
            i++;
        }
        if (i == start) {
            return List.of();
        }
        String value = line.substring(start, i);
        if (NUMERIC.matcher(value).matches()) {
            return List.of();
        }
        return List.of(new Candidate(value, start + 1));
    }

    private static boolean isBareScalarFile(String relativeFile) {
        int dot = relativeFile.lastIndexOf('.');
        return dot >= 0 && BARE_SCALAR_EXTENSIONS.contains(relativeFile.substring(dot).toLowerCase());
    }

    /**
     * 值内容是否像真实密钥：带典型厂商前缀直接判为密钥（前缀是高置信信号）；
     * 否则按"够长 + 字符类别混合 + 香农熵"判定。
     */
    private boolean looksLikeSecret(String value) {
        if (!GENERIC_ALNUM.matcher(value).find()) {
            return false;
        }
        if (value.length() < minLength) {
            return false;
        }
        if (alnumClasses(value) < minClasses) {
            return false;
        }
        return Entropy.shannonDensity(value) >= minEntropy;
    }

    /**
     * 字母数字类别数（小写 / 大写 / 数字），范围 {@code [0,3]}。
     * <p>刻意忽略符号、分隔符（{@code - _ / + = .} 等）、空白与控制字符，因为这些在日期、
     * 路径、ID 等非密钥串里极常见，不应算作"多样性"。这是密钥判定特有的启发式信号，
     * 熵模块不提供该算法，故内联保留。</p>
     */
    private static int alnumClasses(String s) {
        int mask = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') {
                mask |= 1;
            } else if (c >= 'A' && c <= 'Z') {
                mask |= 2;
            } else if (c >= '0' && c <= '9') {
                mask |= 4;
            }
        }
        return Integer.bitCount(mask);
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

    /** 候选值：字符串内容 + 起始列号（1 基，用于报告定位）。 */
    private record Candidate(String value, int column) {
    }

    /** 常规数据结构豁免项：名称便于调试与报告，pattern 用于整值匹配判定。 */
    private record KnownFormat(String name, Pattern pattern) {

        KnownFormat(String name, String regex) {
            this(name, Pattern.compile(regex));
        }
    }
}
