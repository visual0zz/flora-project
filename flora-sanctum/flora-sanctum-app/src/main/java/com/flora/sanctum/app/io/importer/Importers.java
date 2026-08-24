package com.flora.sanctum.app.io.importer;

import com.flora.sanctum.app.io.importer.kdbx.KdbxImporter;

import java.util.List;

/**
 * 导入器注册表：集中管理所有支持的外部格式。新增格式只需在此登记对应 {@link Importer} 实现，
 * UI 通过 {@link #forFile} 按文件自动分派，无需改动调用方。
 */
public final class Importers {

    private static final List<Importer> REGISTRY = List.of(
            new KdbxImporter()
            // 后续格式（CSV、1Password 等）在此追加：, new CsvImporter()
    );

    private Importers() {
    }

    public static List<Importer> all() {
        return REGISTRY;
    }
}
