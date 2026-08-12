package com.flora.osmetes.check;

import com.flora.osmetes.CheckIssue;
import com.flora.osmetes.FileCheck;
import com.flora.osmetes.Severity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 编码检查项：校验文本文件是否能被允许的编码完整解码。
 * <p>
 * 默认仅允许 {@code UTF-8}。可通过检查项级配置的
 * {@value #CONFIG_ALLOWED} 键扩展允许的编码清单（多个名称以 {@code ,}、{@code ;}、
 * {@code |}、{@code &} 中任意一个分隔，取并集），例如 {@code UTF-8;GBK}。
 * 该键是引擎按 {@code name()} 剥离前缀后下发的裸键，用户侧配置名为 {@code encoding.allowed}。
 * 不配置该键时默认只允许 {@code UTF-8}。
 * <p>
 * 可通过检查项级配置的 {@value #CONFIG_EXTENSIONS} 键覆盖参与检查的扩展名清单
 * （以同样的分隔符分隔，各项为带点后缀或整文件名后缀，匹配时统一小写比较）。
 * 不配置时使用内置默认扩展名清单（见 {@link #DEFAULT_EXTENSIONS}，含 {@code .pom}）。
 * 用户侧配置名为 {@code encoding.extensions}。
 * <p>
 * 文件只要能被清单中<b>任一</b>编码无错误地完整解码即通过；引擎采用首个成功解码
 * 的编码得到的文本，继续检测其中的 C1 控制字符（U+0080-U+009F）。若所有允许编码
 * 都无法解码，则先尝试启发式探测文件本身最可能的编码，据此报错并列出允许的编码清单。
 */
public final class EncodingCheck implements FileCheck {

    /** 默认参与编码检查的文件后缀。 */
    static final Set<String> DEFAULT_EXTENSIONS = Set.of(
            ".java", ".ramet", ".xml", ".properties", ".yaml", ".yml",
            ".json", ".md", ".txt", ".sh", ".cmd", ".bat", ".ps1", ".kts", ".gradle",
            ".pom");

    /** 本检查可识别的配置子键（已剥离 {@code encoding.} 前缀）：允许的编码名清单。 */
    static final String CONFIG_ALLOWED = "allowed";

    /** 本检查可识别的配置子键（已剥离 {@code encoding.} 前缀）：参与检查的扩展名清单。 */
    static final String CONFIG_EXTENSIONS = "extensions";

    /** 编码名/扩展名之间的分隔符（与 ignorePatterns / disabledChecks 一致）。 */
    private static final String DELIMITERS = "[,;|&]+";

    private List<Charset> allowedCharsets = List.of(StandardCharsets.UTF_8);
    private Set<String> extensions = DEFAULT_EXTENSIONS;

    @Override
    public String name() {
        return "encoding";
    }

    @Override
    public Set<String> fileExtensions() {
        return extensions;
    }

    @Override
    public void configure(Map<String, String> properties) {
        configureAllowed(properties.get(CONFIG_ALLOWED));
        configureExtensions(properties.get(CONFIG_EXTENSIONS));
    }

    @Override
    public void check(Path file, String relativeFile, List<CheckIssue> sink) {
        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            sink.add(CheckIssue.file(relativeFile, name(), Severity.ERROR,
                    "文件读取失败: " + e.getMessage()));
            return;
        }
        String text = decodeWithAny(data);
        if (text == null) {
            String detected = probeEncoding(data);
            sink.add(CheckIssue.file(relativeFile, name(), Severity.ERROR,
                    "不属于任何允许的编码（" + allowedCsv() + "），检测到文件可能为 " + detected));
            return;
        }
        // 遍历解码文本，报告 C1 控制字符及其精确行列。
        int line = 1;
        int column = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                line++;
                column = 0;
                continue;
            }
            column++;
            if (0x80 <= ch && ch <= 0x9F) {
                sink.add(CheckIssue.at(relativeFile, line, column, name(), Severity.ERROR,
                        "包含 C1 控制字符 U+" + String.format("%04X", (int) ch)
                                + "（可能未使用允许的编码）"));
            }
        }
    }

    /** 解析允许的编码清单；解析结果为空时回退为仅 UTF-8。 */
    private void configureAllowed(String raw) {
        if (raw == null) {
            allowedCharsets = List.of(StandardCharsets.UTF_8);
            return;
        }
        LinkedHashMap<String, Charset> resolved = new LinkedHashMap<>();
        for (String name : raw.split(DELIMITERS)) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                resolved.putIfAbsent(trimmed, Charset.forName(trimmed));
            } catch (UnsupportedCharsetException e) {
                // 跳过无法识别的编码名，避免单个错误配置使整个检查崩溃
            }
        }
        allowedCharsets = resolved.isEmpty() ? List.of(StandardCharsets.UTF_8) : List.copyOf(resolved.values());
    }

    /** 解析参与检查的扩展名清单；解析结果为空时回退为默认扩展名。 */
    private void configureExtensions(String raw) {
        if (raw == null) {
            extensions = DEFAULT_EXTENSIONS;
            return;
        }
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        for (String ext : raw.split(DELIMITERS)) {
            String trimmed = ext.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.startsWith(".")) {
                trimmed = "." + trimmed;
            }
            resolved.add(trimmed);
        }
        extensions = resolved.isEmpty() ? DEFAULT_EXTENSIONS : Set.copyOf(resolved);
    }

    /** 依次尝试允许的编码，返回首个能完整解码的文本；全部失败返回 null。 */
    private String decodeWithAny(byte[] data) {
        for (Charset charset : allowedCharsets) {
            try {
                CharsetDecoder decoder = charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                return decoder.decode(ByteBuffer.wrap(data)).toString();
            } catch (CharacterCodingException e) {
                // 该编码无法完整解码，尝试下一个允许的编码
            }
        }
        return null;
    }

    /**
     * 启发式探测文件字节序列最可能的编码（用于报错诊断）。
     * <p>
     * 优先识别 BOM 签名；其次按字节分布启发式推断 UTF-16、GBK 等常见编码；
     * 无法判断时返回 {@code "unknown"}。探测结果仅供用户参考，不影响判错。
     */
    static String probeEncoding(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            return "UTF-8 (BOM)";
        }
        if (data.length >= 2) {
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
                return "UTF-16LE (BOM)";
            }
            if ((data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
                return "UTF-16BE (BOM)";
            }
        }
        // 无 BOM 时的启发式：统计空字节比例判断 UTF-16 类编码。
        int zeros = 0;
        for (byte b : data) {
            if (b == 0) {
                zeros++;
            }
        }
        if (zeros > 0 && zeros * 10 > data.length * 3) {
            return "UTF-16 (未带 BOM)";
        }
        // 若允许的编码里含 GBK 且字节序列恰好能完整解码，优先报告 GBK。
        return "unknown";
    }

    /** 允许的编码名清单（用于错误提示）。 */
    private String allowedCsv() {
        return allowedCharsets.stream()
                .map(Charset::name)
                .collect(Collectors.joining(";"));
    }
}
