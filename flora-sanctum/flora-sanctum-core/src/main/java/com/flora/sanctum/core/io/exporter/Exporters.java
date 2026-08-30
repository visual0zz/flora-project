package com.flora.sanctum.core.io.exporter;

import java.util.List;

/**
 * 导出器注册表：集中管理所有支持的 Sanctum 自有格式（CSV、JSON）。
 * UI 通过 {@link Exporter#forFormatName} 按格式名选取，无需改动调用方。
 */
public final class Exporters {

    private static final List<Exporter> REGISTRY = List.of(
            new SanctumCsvExporter(),
            new SanctumJsonExporter()
    );

    private Exporters() {
    }

    public static List<Exporter> all() {
        return REGISTRY;
    }
}
