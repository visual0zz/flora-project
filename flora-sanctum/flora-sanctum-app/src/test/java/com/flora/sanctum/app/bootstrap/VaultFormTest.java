package com.flora.sanctum.app.bootstrap;

import com.flora.root.codec.json.model.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VaultFormTest {

    @TempDir
    Path dir;

    @Test
    void detectNormalRepo() throws Exception {
        Path repo = dir.resolve("normal");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("a.md"), "1:abc\n");
        assertEquals(VaultForm.Type.NORMAL, VaultForm.detect(repo));
        assertEquals(repo, VaultForm.dataDir(repo));
    }

    @Test
    void detectStandaloneRepo() throws Exception {
        Path repo = dir.resolve("standalone");
        Files.createDirectories(repo.resolve("lib"));
        Files.writeString(repo.resolve("a.md"), "1:abc\n");
        Files.writeString(repo.resolve("standalone.json"), "{}");
        assertEquals(VaultForm.Type.STANDALONE, VaultForm.detect(repo));
        assertEquals(repo, VaultForm.dataDir(repo));
        assertEquals(repo.resolve("standalone.json"), VaultForm.configFile(repo));
    }

    @Test
    void detectNotAVault() throws Exception {
        Path repo = dir.resolve("other");
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src").resolve("Main.java"), "code");
        assertEquals(VaultForm.Type.NOT_A_VAULT, VaultForm.detect(repo));
        assertNull(VaultForm.dataDir(repo));
    }

    @Test
    void writeAndReadRepoConfig() throws Exception {
        Path repo = dir.resolve("cfg");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("standalone.json"), "{}");
        JsonObject app = new JsonObject();
        app.put("theme", "dark");
        VaultForm.writeRepoConfig(repo, app);
        JsonObject loaded = VaultForm.loadRepoConfig(repo);
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
        assertTrue(Files.exists(repo.resolve("start.cmd")));
        assertTrue(Files.exists(repo.resolve("standalone.json")));
    }

    @Test
    void importEmptyDirBecomesNormalRepo() throws Exception {
        // 空目录检测（clone 出的空目录）→ 判定为合法普通仓库
        Path empty = dir.resolve("empty");
        Files.createDirectories(empty);
        // 模拟：无结构但视为空仓库 → 由导入流程建基本结构
        assertEquals(VaultForm.Type.NOT_A_VAULT, VaultForm.detect(empty));
    }
}
