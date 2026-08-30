package com.flora.sanctum.core.io.exporter;

import com.flora.sanctum.core.model.tree.ObjectTree;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 一种外部数据格式的导出器。每种格式（Sanctum CSV、Sanctum JSON…）实现本接口并放入 {@link Exporters} 注册表。
 * <p>实现只读写模型与磁盘，不持有 UI 状态。</p>
 */
public interface Exporter {

    /** 人类可读格式名（菜单、日志）。 */
    String formatName();

    /** 是否可处理该文件（按扩展名判断）。 */
    boolean supports(Path file);

    /** 执行导出，把 {@code tree} 写入 {@code file}；失败抛 {@link ExportException}。 */
    void exportTo(Path file, ObjectTree tree) throws ExportException;

    /** 文件扩展名匹配（不区分大小写）。 */
    default boolean hasExtension(Path file, String ext) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        return name.toLowerCase().endsWith("." + ext.toLowerCase());
    }

    /** 从注册表中按格式名选取导出器。 */
    static Optional<Exporter> forFormatName(String name) {
        return Exporters.all().stream()
                .filter(e -> e.formatName().equalsIgnoreCase(name))
                .findFirst();
    }

    /** 所有已注册格式名（供 UI 弹窗列出）。 */
    static List<String> formatNames() {
        return Exporters.all().stream().map(Exporter::formatName).toList();
    }
}
