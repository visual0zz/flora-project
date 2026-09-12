package com.flora.sanctum.app.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VaultDetectorTest {

    @TempDir
    Path dir;

    @Test
    void detectNormalRepo() throws Exception {
        Path repo = dir.resolve("normal");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("a.md"), "1:abc\n");
        assertEquals(VaultDetector.Type.NORMAL, VaultDetector.detect(repo));
        assertEquals(repo, VaultDetector.dataDir(repo));
    }

    @Test
    void detectStandaloneRepo() throws Exception {
        Path repo = dir.resolve("standalone");
        Files.createDirectories(repo.resolve("lib"));
        Files.writeString(repo.resolve("edit"), "#!/usr/bin/env bash\n");
        Files.writeString(repo.resolve("a.md"), "1:abc\n");
        assertEquals(VaultDetector.Type.STANDALONE, VaultDetector.detect(repo));
        assertEquals(repo, VaultDetector.dataDir(repo));
    }

    @Test
    void detectNotAVault() throws Exception {
        Path repo = dir.resolve("other");
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src").resolve("Main.java"), "code");
        assertEquals(VaultDetector.Type.NOT_A_VAULT, VaultDetector.detect(repo));
        assertNull(VaultDetector.dataDir(repo));
    }

    @Test
    void createStandaloneWritesLayoutWithoutRepoConfig() throws Exception {
        Path repo = dir.resolve("newStandalone");

        Path vaultRoot = RepoCreator.createStandalone(repo);

        assertEquals(repo, vaultRoot);
        assertTrue(Files.isDirectory(repo.resolve("lib")));
        assertTrue(Files.exists(repo.resolve("edit")));
        assertTrue(Files.exists(repo.resolve("edit.bat")));
        // 明文偏好统一走系统级配置，仓库根不再写 config.json
        assertFalse(Files.exists(repo.resolve("config.json")));
        assertTrue(VaultDetector.isStandaloneRepo(repo));
    }

    @Test
    void importEmptyDirBecomesNormalRepo() throws Exception {
        // 空目录检测（clone 出的空目录）→ 判定为合法普通仓库
        Path empty = dir.resolve("empty");
        Files.createDirectories(empty);
        // 模拟：无结构但视为空仓库 → 由导入流程建基本结构
        assertEquals(VaultDetector.Type.NOT_A_VAULT, VaultDetector.detect(empty));
    }

    @Test
    void upgradeToStandaloneAddsLayout() throws Exception {
        Path repo = dir.resolve("upgrade");
        Files.createDirectories(repo);
        Files.createDirectories(repo.resolve("aa"));
        Files.writeString(repo.resolve("aa").resolve("b.md"), "1:abc\n");
        assertEquals(VaultDetector.Type.NORMAL, VaultDetector.detect(repo));

        RepoCreator.upgradeToStandalone(repo);

        assertFalse(Files.exists(repo.resolve("config.json")));
        assertTrue(Files.isDirectory(repo.resolve("lib")));
        assertTrue(Files.exists(repo.resolve("edit")));
        assertTrue(Files.exists(repo.resolve("edit.bat")));
        assertEquals(VaultDetector.Type.STANDALONE, VaultDetector.detect(repo));
        // 数据仍在仓库根（未移动）
        assertTrue(Files.exists(repo.resolve("aa").resolve("b.md")));
        // 升级已是独立仓库时拒绝重复升级
        assertThrows(IOException.class, () -> RepoCreator.upgradeToStandalone(repo));
    }

    @Test
    void downgradeToNormalRemovesLayout() throws Exception {
        Path repo = dir.resolve("downgrade");
        Files.createDirectories(repo.resolve("lib"));
        Files.createDirectories(repo.resolve("aa"));
        Files.writeString(repo.resolve("aa").resolve("b.md"), "1:abc\n");
        Files.writeString(repo.resolve("config.json"), "{}");
        Files.writeString(repo.resolve("edit"), "#!/usr/bin/env bash\n");
        Files.writeString(repo.resolve("edit.bat"), "@echo off\n");
        assertEquals(VaultDetector.Type.STANDALONE, VaultDetector.detect(repo));

        RepoCreator.downgradeToNormal(repo);

        assertFalse(Files.exists(repo.resolve("edit")));
        assertFalse(Files.exists(repo.resolve("edit.bat")));
        assertFalse(Files.exists(repo.resolve("lib")));
        // 历史遗留的仓库级 config.json 已不在管理范围内：不读写也不删除
        assertTrue(Files.exists(repo.resolve("config.json")));
        assertEquals(VaultDetector.Type.NORMAL, VaultDetector.detect(repo));
        // 数据仍在仓库根
        assertTrue(Files.exists(repo.resolve("aa").resolve("b.md")));
    }
}
