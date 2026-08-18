package com.flora.sanctum.app;

import com.flora.sanctum.app.bootstrap.VaultForm;
import com.flora.sanctum.app.ui.SanctumGui;
import com.flora.sanctum.app.ui.SelectScreen;

import java.nio.file.Path;

/**
 * flora-sanctum 应用入口（单一可执行 jar）。GUI 是唯一交互方式。
 * <p>
 * 启动时先做形态识别（见设计"形态与启动"）：
 * - 独立仓库形态（启动脚本经 -Dflora.repo 传入仓库根）→ 直接解锁该仓库；
 * - 应用形态 → 进入选择界面（新建 / 导入 / 打开）。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // 仓库根经启动脚本 -Dflora.repo 传入（与配置/脚本同目录）；未传则回退到当前工作目录
        String repoProp = System.getProperty("flora.repo");
        Path repoRoot = repoProp != null && !repoProp.isBlank()
                ? Path.of(repoProp).toAbsolutePath()
                : Path.of("").toAbsolutePath();
        if (VaultForm.detect(repoRoot) == VaultForm.Type.STANDALONE) {
            Path root = VaultForm.vaultRoot(repoRoot);
            SanctumGui.launchDirect(repoRoot, root);
        } else {
            // 应用形态 → 选择界面（新建/导入/打开）
            new SelectScreen(vaultRoot -> SanctumGui.launchOpen(vaultRoot)).show();
        }
    }
}
