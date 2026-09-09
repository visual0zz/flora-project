package com.flora.sanctum.core.io.importer;

import com.flora.sanctum.core.io.importer.kdbx.KdbxImporter;

import java.util.List;

/**
 * 导入器注册表：集中管理所有支持的外部格式。新增格式只需在此登记对应 {@link Importer} 实现，
 * UI 通过 {@link #forFile} / {@link #forFormatName} 选取，无需改动调用方。
 */
public final class Importers {

    private static final List<Importer> REGISTRY = List.of(
            new KdbxImporter(),
            new SanctumCsvImporter(),
            new SanctumJsonImporter()
    );

    private Importers() {
    }

    public static List<Importer> all() {
        return REGISTRY;
    }
}
