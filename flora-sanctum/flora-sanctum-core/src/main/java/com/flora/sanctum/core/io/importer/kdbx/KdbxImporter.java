package com.flora.sanctum.core.io.importer.kdbx;

import com.flora.sanctum.core.io.importer.ImportContext;
import com.flora.sanctum.core.io.importer.ImportException;
import com.flora.sanctum.core.io.importer.ImportResult;
import com.flora.sanctum.core.io.importer.Importer;
import com.flora.sanctum.kdbx.KdbxDocument;
import com.flora.sanctum.kdbx.KdbxReader;
import com.flora.sanctum.kdbx.KdbxReadException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * KeePassXC / KeePass 的 KDBX 导入器。注册于 {@link com.flora.sanctum.core.io.importer.Importers}，
 * 通过 {@code Importer.forFile(path)} 自动分派。
 * <p>读取/解密由 {@code flora-sanctum-kdbx} 模块完成（{@link KdbxReader}），本导入器仅负责
 * 把读取结果 {@link KdbxDocument} 映射进 Sanctum 模型。</p>
 */
public final class KdbxImporter implements Importer {

    @Override
    public String formatName() {
        return "KeePassXC / KeePass (KDBX)";
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
            KdbxDocument doc = KdbxReader.read(data, ctx.password(), keyFileBytes);
            return KdbxMapper.map(doc, ctx);
        } catch (KdbxReadException e) {
            throw new ImportException("KDBX 读取失败: " + e.getMessage(), e);
        } finally {
            ctx.clearSecrets();
        }
    }

    // 暴露给测试：列出支持的扩展名（与 supports 一致）。
    static List<String> extensions() {
        return List.of("kdbx");
    }
}
