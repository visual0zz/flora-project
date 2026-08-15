package com.flora.sanctum.sync;

import com.flora.sanctum.model.Sanctum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SyncServiceTest {

    @TempDir
    Path dir;

    @Test
    void initAndCommit() throws Exception {
        // 先创建库（会写文件）
        Path vault = dir.resolve("vault");
        Sanctum.createAndUnlock(vault, "pw".toCharArray());

        SyncService sync = new SyncService(vault);
        sync.initIfNeeded();
        assertTrue(sync.isGitRepo());

        sync.commit("initial");
        assertTrue(Files.isDirectory(vault.resolve(".git")));
        assertTrue(Files.exists(vault.resolve(".git/HEAD")));
    }

    @Test
    void cloneAndCommitRoundTrip() throws Exception {
        // 创建源库 + git init + commit
        Path src = dir.resolve("src");
        Sanctum.createAndUnlock(src, "pw".toCharArray());
        SyncService srcSync = new SyncService(src);
        srcSync.initIfNeeded();
        srcSync.commit("v1");

        // 克隆到目标
        Path dst = dir.resolve("dst");
        org.eclipse.jgit.api.Git.cloneRepository()
                .setURI(src.toUri().toString())
                .setDirectory(dst.toFile())
                .call()
                .close();

        assertTrue(Files.isDirectory(dst.resolve(".git")));
        assertTrue(Files.list(dst).count() > 0);
    }
}
