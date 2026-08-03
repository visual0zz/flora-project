package com.flora.osmetes.check;

import com.flora.osmetes.CheckIssue;
import com.flora.osmetes.FileCheck;
import com.flora.osmetes.Severity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 编码检查项：校验文本文件是否为合法 UTF-8。
 * <p>
 * 使用 {@link CharsetDecoder} 配合 {@link CodingErrorAction#REPORT} 对文件字节做
 * 严格解码，任何非法 UTF-8 序列都会触发 {@link CharacterCodingException} 并报告
 * 错误；同时检测解码文本中残留的 C1 控制字符（U+0080-U+009F）。
 */
public final class EncodingCheck implements FileCheck {

    /** 默认参与编码检查的文件后缀。 */
    private static final Set<String> EXTENSIONS = Set.of(
            ".java", ".ramet", ".xml", ".properties", ".yaml", ".yml",
            ".json", ".md", ".txt", ".sh", ".cmd", ".bat", ".ps1", ".kts", ".gradle");

    private static final CharsetDecoder STRICT_UTF8 = StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

    @Override
    public String name() {
        return "encoding";
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
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
        String text;
        try {
            text = STRICT_UTF8.decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            sink.add(CheckIssue.file(relativeFile, name(), Severity.ERROR,
                    "非 UTF-8 编码，存在非法字节序列"));
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
                                + "（文件可能不是 UTF-8 编码）"));
            }
        }
    }
}
