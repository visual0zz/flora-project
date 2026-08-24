package com.flora.sanctum.app.io.importer;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 一种外部数据格式的导入器。每种格式（KeePassXC、CSV…）实现本接口并放入 {@link Importers} 注册表。
 * <p>实现不应持有 UI 状态；UI/进度通过 {@link ImportContext} 传入。</p>
 */
public interface Importer {

    /** 人类可读格式名（菜单、日志）。 */
    String formatName();

    /** 是否可处理该文件（按扩展名或魔数判断）。 */
    boolean supports(Path file);

    /** 执行导入，写入 {@code ctx.tree()}；失败抛 {@link ImportException}。 */
    ImportResult importFile(Path file, ImportContext ctx) throws ImportException;

    /** 文件扩展名匹配（不区分大小写）。 */
    default boolean hasExtension(Path file, String ext) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        return name.toLowerCase().endsWith("." + ext.toLowerCase());
    }

    /** 从注册表中按文件选取导入器。 */
    static Optional<Importer> forFile(Path file) {
        return Importers.all().stream().filter(i -> i.supports(file)).findFirst();
    }
}
