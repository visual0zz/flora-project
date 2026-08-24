package com.flora.sanctum.app.io.importer.kdbx;

import com.flora.sanctum.app.io.importer.ImportContext;
import com.flora.sanctum.app.io.importer.ImportException;
import com.flora.sanctum.app.io.importer.ImportResult;
import com.flora.sanctum.app.io.importer.Importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * KeePassXC / KeePass 的 KDBX4 导入器。注册于 {@link com.flora.sanctum.app.io.importer.Importers}，
 * 通过 {@code Importer.forFile(path)} 自动分派。
 */
public final class KdbxImporter implements Importer {

    @Override
    public String formatName() {
        return "KeePassXC / KeePass (KDBX4)";
    }

    @Override
    public boolean supports(Path file) {
        return hasExtension(file, "kdbx");
    }

    @Override
    public ImportResult importFile(Path file, ImportContext ctx) throws ImportException {
        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ImportException("无法读取文件: " + file, e);
        }
        byte[] keyFileBytes = null;
        if (ctx.keyFile() != null) {
            try {
                keyFileBytes = Files.readAllBytes(ctx.keyFile());
            } catch (IOException e) {
                throw new ImportException("无法读取密钥文件: " + ctx.keyFile(), e);
            }
        }
        try {
            KdbxDocument doc = KdbxParser.parse(data, ctx.password(), keyFileBytes);
            return KdbxMapper.map(doc, ctx);
        } finally {
            ctx.clearSecrets();
        }
    }

    // 暴露给测试：列出支持的扩展名（与 supports 一致）。
    static List<String> extensions() {
        return List.of("kdbx");
    }
}
