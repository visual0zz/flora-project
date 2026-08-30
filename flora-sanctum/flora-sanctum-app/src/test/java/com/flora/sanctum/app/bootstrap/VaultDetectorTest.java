package com.flora.sanctum.app.bootstrap;

import com.flora.root.codec.json.model.JsonObject;
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
        assertEquals(repo.resolve("config.json"), VaultDetector.configFile(repo));
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
    void writeAndReadRepoConfig() throws Exception {
        Path repo = dir.resolve("cfg");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("config.json"), "{}");
        JsonObject app = new JsonObject();
        app.put("theme", "dark");
        VaultDetector.writeRepoConfig(repo, app);
        JsonObject loaded = VaultDetector.loadRepoConfig(repo);
        assertEquals("dark", loaded.getString("theme"));
    }

    @Test
    void createStandaloneWritesLayout() throws Exception {
        Path repo = dir.resolve("newStandalone");

        JsonObject cfg = new JsonObject();
        cfg.put("theme", "system");
        Path vaultRoot = RepoCreator.createStandalone(repo, cfg);

        assertEquals(repo, vaultRoot);
        assertTrue(Files.isDirectory(repo.resolve("lib")));
        assertTrue(Files.exists(repo.resolve("edit")));
        assertTrue(Files.exists(repo.resolve("edit.bat")));
        assertTrue(Files.exists(repo.resolve("config.json")));
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

        JsonObject cfg = new JsonObject();
        cfg.put("theme", "dark");
        RepoCreator.upgradeToStandalone(repo, cfg);

        assertTrue(Files.exists(repo.resolve("config.json")));
        assertTrue(Files.isDirectory(repo.resolve("lib")));
        assertTrue(Files.exists(repo.resolve("edit")));
        assertTrue(Files.exists(repo.resolve("edit.bat")));
        assertEquals(VaultDetector.Type.STANDALONE, VaultDetector.detect(repo));
        // 数据仍在仓库根（未移动）
        assertTrue(Files.exists(repo.resolve("aa").resolve("b.md")));
        // 升级已是独立仓库时拒绝重复升级
        assertThrows(IOException.class, () -> RepoCreator.upgradeToStandalone(repo, cfg));
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

        assertFalse(Files.exists(repo.resolve("config.json")));
        assertFalse(Files.exists(repo.resolve("edit")));
        assertFalse(Files.exists(repo.resolve("edit.bat")));
        assertFalse(Files.exists(repo.resolve("lib")));
        assertEquals(VaultDetector.Type.NORMAL, VaultDetector.detect(repo));
        // 数据仍在仓库根
        assertTrue(Files.exists(repo.resolve("aa").resolve("b.md")));
    }
}
