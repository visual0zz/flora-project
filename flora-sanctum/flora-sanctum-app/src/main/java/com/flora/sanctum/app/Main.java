package com.flora.sanctum.app;

import com.flora.sanctum.app.bootstrap.VaultDetector;
import com.flora.sanctum.app.ui.SanctumGui;

import java.nio.file.Path;

/**
 * flora-sanctum 应用入口（单一可执行 jar）。GUI 是唯一交互方式。
 * <p>
 * 启动时在 jar 内做形态判定（见设计"形态与启动"）：
 * - 孤立仓库形态（jar 同目录/工作目录存在 standalone.json）→ 直接进入该仓库的解锁页；
 * - 应用形态 → 进入历史仓库列表页（新建/导入/打开合并于此）。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path standaloneRoot = VaultDetector.detectStandaloneRoot();
        if (standaloneRoot != null) {
            Path vaultRoot = VaultDetector.vaultRoot(standaloneRoot);
            SanctumGui.launchDirect(standaloneRoot, vaultRoot);
        } else {
            SanctumGui.launch();
        }
    }
}
