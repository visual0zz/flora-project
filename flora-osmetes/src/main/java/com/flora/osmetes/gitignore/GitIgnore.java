package com.flora.osmetes.gitignore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 单个 {@code .gitignore} 文件的解析结果与匹配器。
 * <p>
 * 语义对齐 git 文档（gitignore(5)）：
 * <ul>
 *   <li>空行与以 {@code #} 开头的行是注释；</li>
 *   <li>以 {@code !} 开头的模式取反，同文件内"最后一条匹配生效"；</li>
 *   <li>以 {@code /} 结尾的模式仅匹配目录；</li>
 *   <li>含 {@code /} 的模式锚定到本文件所在目录，不含 {@code /} 的模式
 *       匹配任意层级下的同名文件/目录；</li>
 *   <li>支持 {@code *}、{@code ?}、{@code [...]}（含 {@code [!...]}）、
 *       {@code **} 及 {@code \} 转义。</li>
 * </ul>
 * <p>
 * 本类只负责"按某一份文件判定单个路径"，跨文件的"深者覆盖浅者"组合逻辑
 * 由 {@link GitIgnoreChain} 负责。
 */
public final class GitIgnore {

    /**
     * 单条规则对给定路径的判定结果。
     */
    public enum Match {
        /** 该文件不适用，继续向更浅层的规则询问。 */
        NO_MATCH,
        /** 命中忽略规则。 */
        IGNORED,
        /** 命中取反规则（{@code !} 前缀），表示重新包含。 */
        INCLUDED
    }

    private final Path base;
    private final List<Rule> rules;

    /** 分隔模式串的分隔符正则（与 Osmetes、检查项配置一致）。 */
    private static final String PATTERN_DELIMITERS = "[,;|&]+";

    /** 编译后的单条规则。 */
    private record Rule(Pattern regex, boolean negated, boolean dirOnly) {
    }

    private GitIgnore(Path base, List<Rule> rules) {
        this.base = base;
        this.rules = rules;
    }

    /**
     * 从磁盘读取并解析一个 {@code .gitignore} 文件。
     *
     * @param gitIgnoreFile 待解析的 {@code .gitignore} 文件路径
     * @return 解析结果，规则以文件中的原始顺序排列
     */
    public static GitIgnore load(Path gitIgnoreFile) throws IOException {
        Path abs = gitIgnoreFile.toAbsolutePath().normalize();
        return parse(abs.getParent(), Files.readAllLines(abs, StandardCharsets.UTF_8));
    }

    /**
     * 从文本行解析规则，供测试与程序化构造使用。
     *
     * @param base  该 {@code .gitignore} 所在目录（锚定与相对路径的基准）
     * @param lines 原始文本行
     * @return 解析结果
     */
    public static GitIgnore parse(Path base, List<String> lines) {
        List<Rule> rules = new ArrayList<>();
        for (String raw : lines) {
            String line = stripCommentAndTrailingSpace(raw);
            if (line == null) {
                continue;
            }
            boolean negated = false;
            if (line.startsWith("!")) {
                negated = true;
                line = line.substring(1);
            }
            if (line.isEmpty()) {
                continue; // "!" 单独一行，忽略
            }
            boolean dirOnly = line.endsWith("/");
            if (dirOnly) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                continue; // "/" 单独一行，忽略
            }
            String regex = globToRegex(line);
            try {
                rules.add(new Rule(Pattern.compile(regex), negated, dirOnly));
            } catch (PatternSyntaxException e) {
                // 无法编译为合法正则的模式跳过该条规则
            }
        }
        return new GitIgnore(base.toAbsolutePath().normalize(), List.copyOf(rules));
    }

    /**
     * 从分隔符连接的配置字符串解析规则。
     * <p>
     * 供 Maven 插件等外部配置使用：以 {@code ,}、{@code ;}、{@code |}、{@code &}
     * 中任意一个作为分隔符，多个模式取并集（都忽略），语义与 {@link #parse} 一致。
     * 各段首尾空白会被去除，空段忽略。例如 {@code "absent/;*.log"} 表示忽略所有
     * 路径中含名为 {@code absent} 的目录的文件以及所有 {@code .log} 文件。
     *
     * @param base     扫描根目录（模式锚定基准）
     * @param patterns 分隔符连接的模式串，可为空字符串
     * @return 解析结果
     */
    public static GitIgnore parsePatterns(Path base, String patterns) {
        if (patterns == null) {
            return parse(base, List.of()); // 未配置时退化为空规则集
        }
        List<String> lines = new ArrayList<>();
        for (String segment : patterns.split(PATTERN_DELIMITERS)) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return parse(base, lines);
    }

    /**
     * 判定 {@code path} 是否被本文件忽略。
     * <p>
     * 仅当 {@code path} 位于本文件所在目录之下时参与判定；对目录本身的询问
     * （路径等于 {@code base}）不适用，返回 {@link Match#NO_MATCH}。
     * 同文件内"最后一条匹配生效"，故从尾到头扫描。
     *
     * @param path        待判定的路径（绝对或相对均可）
     * @param isDirectory 目标是否为目录（仅目录模式依赖此信息）
     * @return 判定结果
     */
    public Match matches(Path path, boolean isDirectory) {
        Path abs = path.toAbsolutePath().normalize();
        if (abs.equals(base) || !abs.startsWith(base)) {
            return Match.NO_MATCH;
        }
        String rel = base.relativize(abs).toString().replace('\\', '/');
        for (int i = rules.size() - 1; i >= 0; i--) {
            Rule rule = rules.get(i);
            if (rule.dirOnly && !isDirectory) {
                continue; // 仅目录模式不会命中文件
            }
            if (rule.regex.matcher(rel).matches()) {
                return rule.negated ? Match.INCLUDED : Match.IGNORED;
            }
        }
        return Match.NO_MATCH;
    }

    /** 该 {@code .gitignore} 所在目录（绝对规范化）。 */
    Path base() {
        return base;
    }

    /**
     * 去除注释与尾部空格。
     * <p>
     * 遵循 git 规则：尾部空格忽略，除非用反斜杠转义；以 {@code #} 开头的行是注释。
     *
     * @return 有效模式，空行/注释返回 {@code null}
     */
    private static String stripCommentAndTrailingSpace(String raw) {
        String line = raw;
        while (!line.isEmpty() && line.endsWith(" ") && !isEscapedTrailingSpace(line)) {
            line = line.substring(0, line.length() - 1);
        }
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        return line;
    }

    /**
     * 判断行尾空格是否被反斜杠转义（反斜杠数量为奇数即转义）。
     */
    private static boolean isEscapedTrailingSpace(String s) {
        int backslashes = 0;
        for (int i = s.length() - 2; i >= 0 && s.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    /**
     * 将 git glob 模式编译为正则表达式。
     * <p>
     * 调用前已完成取反前缀、尾部斜杠的剥离；此处的斜杠语义：
     * 含斜杠的模式锚定到 {@code base}，不含斜杠的模式为"任意层级同名"匹配。
     */
    private static String globToRegex(String pattern) {
        boolean anchored = pattern.startsWith("/") || pattern.contains("/");
        if (pattern.startsWith("/")) {
            pattern = pattern.substring(1); // 剥离锚定前缀斜杠
        }
        StringBuilder sb = new StringBuilder("^");
        if (pattern.startsWith("**/")) {
            sb.append("(?:.*/)?");
            pattern = pattern.substring(3);
        }
        int n = pattern.length();
        int i = 0;
        while (i < n) {
            char c = pattern.charAt(i);
            if (c == '*') {
                int j = i;
                while (j < n && pattern.charAt(j) == '*') {
                    j++;
                }
                boolean doubleStar = (j - i) >= 2;
                if (doubleStar) {
                    if (j == n && i == 0) {
                        // 整个模式即 **（含 /** 剥离后的情形）：匹配一切
                        sb.append(".*");
                        i = j;
                    } else if (j < n && pattern.charAt(j) == '/') {
                        if (i > 0 && pattern.charAt(i - 1) == '/') {
                            // a/**/b：前导斜杠已输出，这里补 (?:.*/)?
                            sb.append("(?:.*/)?");
                            i = j + 1;
                        } else {
                            // a**/b：** 未夹在斜杠间，视同两个 *：不跨目录
                            sb.append("[^/]*");
                            i = j;
                        }
                    } else if (i > 0 && pattern.charAt(i - 1) == '/') {
                        // a/** → a/(?:.*)?（尾部，匹配其内一切）
                        sb.append("(?:.*)?");
                        i = j;
                    } else {
                        // 未贴近斜杠的 ** 视同两个 *：不跨目录
                        sb.append("[^/]*");
                        i = j;
                    }
                } else {
                    sb.append("[^/]*");
                    i = j;
                }
            } else if (c == '?') {
                sb.append("[^/]");
                i++;
            } else if (c == '[') {
                int close = pattern.indexOf(']', i + 1);
                if (close < 0) {
                    sb.append(Pattern.quote("["));
                    i++;
                } else {
                    String content = pattern.substring(i + 1, close);
                    if (content.startsWith("!")) {
                        content = "^" + content.substring(1); // git 用 ! 表示类取反
                    }
                    sb.append('[').append(content).append(']');
                    i = close + 1;
                }
            } else if (c == '\\') {
                i++;
                if (i < n) {
                    sb.append(Pattern.quote(String.valueOf(pattern.charAt(i))));
                    i++;
                }
            } else if (c == '/') {
                sb.append('/');
                i++;
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        sb.append('$');
        String regex = sb.toString();
        if (!anchored) {
            // 无斜杠模式：匹配任意层级下的同名条目
            regex = "(?:.*/)?" + regex.substring(1);
        }
        return regex;
    }
}
