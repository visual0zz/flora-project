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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 编码检查项：校验文本文件是否能被允许的编码完整解码。
 * <p>
 * 默认仅允许 {@code UTF-8}。可通过检查项级配置的
 * {@value #CONFIG_ALLOWED} 键扩展允许的编码清单（多个名称以 {@code ,}、{@code ;}、
 * {@code |}、{@code &} 中任意一个分隔，取并集），例如 {@code UTF-8;GBK}。
 * <p>
 * 文件只要能被清单中<b>任一</b>编码无错误地完整解码即通过；引擎采用首个成功解码
 * 的编码得到的文本，继续检测其中的 C1 控制字符（U+0080-U+009F）。若所有允许编码
 * 都无法解码，则报告错误并列出允许的编码清单。
 */
public final class EncodingCheck implements FileCheck {

    /** 默认参与编码检查的文件后缀。 */
    private static final Set<String> EXTENSIONS = Set.of(
            ".java", ".ramet", ".xml", ".properties", ".yaml", ".yml",
            ".json", ".md", ".txt", ".sh", ".cmd", ".bat", ".ps1", ".kts", ".gradle");

    /** 本检查可识别的配置键：允许的编码名清单。 */
    static final String CONFIG_ALLOWED = "encoding.allowed";

    /** 编码名之间的分隔符（与 ignorePatterns / disabledChecks 一致）。 */
    private static final String DELIMITERS = "[,;|&]+";

    private List<Charset> allowedCharsets = List.of(StandardCharsets.UTF_8);

    @Override
    public String name() {
        return "encoding";
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    @Override
    public void configure(Map<String, String> properties) {
        String raw = properties.get(CONFIG_ALLOWED);
        LinkedHashMap<String, Charset> resolved = new LinkedHashMap<>();
        if (raw != null) {
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
        }
        if (!resolved.isEmpty()) {
            allowedCharsets = List.copyOf(resolved.values());
        } else {
            allowedCharsets = List.of(StandardCharsets.UTF_8);
        }
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
            sink.add(CheckIssue.file(relativeFile, name(), Severity.ERROR,
                    "不属于任何允许的编码（" + allowedCsv() + "）"));
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

    /** 允许的编码名清单（用于错误提示）。 */
    private String allowedCsv() {
        return allowedCharsets.stream()
                .map(Charset::name)
                .collect(Collectors.joining(";"));
    }
}
