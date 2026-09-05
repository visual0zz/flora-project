package com.flora.sanctum.app.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class RepoCreatorRefreshPrecheckTest {

    @TempDir
    Path dir;

    @Test
    void targetNewerBlocksRefresh() throws IOException {
        Path repo = standaloneWithApp("2.0.0");
        assertEquals(RepoCreator.RefreshOutcome.TARGET_NEWER,
                RepoCreator.precheckRefresh(repo, "0.1.0"));
    }

    @Test
    void targetEqualAllowsRefresh() throws IOException {
        Path repo = standaloneWithApp("0.1.0");
        assertEquals(RepoCreator.RefreshOutcome.OK,
                RepoCreator.precheckRefresh(repo, "0.1.0"));
    }

    @Test
    void targetOlderAllowsRefresh() throws IOException {
        Path repo = standaloneWithApp("0.1.0");
        assertEquals(RepoCreator.RefreshOutcome.OK,
                RepoCreator.precheckRefresh(repo, "0.2.0"));
    }

    @Test
    void unknownSelfDoesNotBlock() throws IOException {
        // self 版本未知（开发态/无法解析）时不应误拦截更高的目标仓库
        Path repo = standaloneWithApp("2.0.0");
        assertEquals(RepoCreator.RefreshOutcome.OK,
                RepoCreator.precheckRefresh(repo, null));
    }

    @Test
    void notStandaloneReported() throws IOException {
        Path plain = dir.resolve("plain");
        Files.createDirectories(plain);
        // 独立仓库判定依赖 lib/ + edit 脚本；普通目录不是独立仓
        assertEquals(RepoCreator.RefreshOutcome.NOT_STANDALONE,
                RepoCreator.precheckRefresh(plain, "0.1.0"));
    }

    private Path standaloneWithApp(String version) throws IOException {
        Path repo = dir.resolve("repo-" + version);
        Path lib = repo.resolve("lib");
        Files.createDirectories(lib);
        Files.writeString(repo.resolve("edit"), "#!/usr/bin/env bash\n");
        writeAppJar(lib.resolve("flora-sanctum-app-" + version + ".jar"), version);
        assertTrue(VaultDetector.isStandaloneRepo(repo));
        return repo;
    }

    private void writeAppJar(Path jar, String implementationVersion) throws IOException {
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, implementationVersion);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), mf)) {
            out.putNextEntry(new JarEntry("dummy.txt"));
            out.write("x".getBytes());
            out.closeEntry();
        }
    }
}
