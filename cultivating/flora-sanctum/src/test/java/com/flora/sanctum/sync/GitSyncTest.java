package com.flora.sanctum.sync;

import com.flora.sanctum.crypto.KeyDerivation;
import com.flora.sanctum.crypto.SecureRandomHolder;
import com.flora.sanctum.model.Entry;
import com.flora.sanctum.model.VaultMeta;
import com.flora.sanctum.storage.VaultStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class GitSyncTest {

    @TempDir
    Path tempDir;

    @Test
    void gitInit() {
        Path repoDir = tempDir.resolve("myvault");
        GitBackend.init(repoDir);
        assertTrue(GitBackend.isGitRepo(repoDir));
    }

    @Test
    void gitAddAndCommit() {
        Path repoDir = tempDir.resolve("myvault");
        GitBackend.init(repoDir);
        GitBackend.configureUser(repoDir, "Test User", "test@example.com");

        // Create a file
        assertDoesNotThrow(() -> {
            Files.writeString(repoDir.resolve("test.txt"), "hello");
        });

        CommitPush.addAndCommit(repoDir, "initial commit");
        String log = GitBackend.gitOutput(repoDir, "log", "--oneline");
        assertFalse(log.isEmpty());
    }

    @Test
    void gitCloneLocalBare() throws Exception {
        // Create bare repo as remote
        Path bareRepo = tempDir.resolve("remote.git");
        GitBackend.git(null, "init", "--bare", bareRepo.toAbsolutePath().toString());
        assertTrue(GitBackend.isGitRepo(bareRepo));

        // Create local repo, commit, push
        Path local = tempDir.resolve("local");
        GitBackend.init(local);
        GitBackend.configureUser(local, "Test", "test@test.com");
        Files.writeString(local.resolve("data.txt"), "test content");
        CommitPush.addAndCommit(local, "first commit");
        RemoteConfig.addRemote(local, "origin", bareRepo.toAbsolutePath().toString());
        CommitPush.push(local);

        // Clone to another location
        Path clone = tempDir.resolve("clone");
        GitBackend.cloneRepo(bareRepo.toAbsolutePath().toString(), clone);
        assertTrue(Files.exists(clone.resolve("data.txt")));
    }

    @Test
    void createVaultAndPushToBare() throws Exception {
        // Setup: create bare remote
        Path bareRepo = tempDir.resolve("remote.git");
        GitBackend.git(null, "init", "--bare", bareRepo.toAbsolutePath().toString());

        // Create a vault
        Path vaultDir = tempDir.resolve("vault");
        char[] password = "master-password".toCharArray();
        byte[] salt = new byte[16];
        SecureRandomHolder.get().nextBytes(salt);

        VaultMeta meta = new VaultMeta();
        meta.setSalt(Base64.getEncoder().encodeToString(salt));
        meta.setIterations(KeyDerivation.MIN_ITERATIONS);

        byte[] encKey = KeyDerivation.encryptionKey(KeyDerivation.derive(password, salt,
                KeyDerivation.MIN_ITERATIONS));
        byte[] macKey = KeyDerivation.macKey(KeyDerivation.derive(password, salt,
                KeyDerivation.MIN_ITERATIONS));

        // Create vault and init git
        VaultStore.create(vaultDir, meta, macKey);
        GitBackend.init(vaultDir);
        GitBackend.configureUser(vaultDir, "Vault User", "vault@local");
        CommitPush.addAndCommit(vaultDir, "initialize vault");

        // Add an entry
        Entry entry = new Entry("GitHub", "user", "pass123", null, null, null);
        VaultStore.saveEntry(vaultDir, entry, encKey);

        // Commit and push
        CommitPush.addAndCommit(vaultDir, "add GitHub entry");
        RemoteConfig.addRemote(vaultDir, "origin", bareRepo.toAbsolutePath().toString());
        CommitPush.push(vaultDir);

        // Clone and verify
        Path clonedVault = tempDir.resolve("cloned-vault");
        GitBackend.cloneRepo(bareRepo.toAbsolutePath().toString(), clonedVault);
        assertTrue(Files.exists(clonedVault.resolve("meta.json")));
        assertTrue(Files.exists(clonedVault.resolve("meta.json.sig")));
        assertTrue(Files.exists(clonedVault.resolve("entries")));
    }

    @Test
    void pullWithNoConflicts() throws Exception {
        // Setup: bare repo
        Path bareRepo = tempDir.resolve("remote.git");
        GitBackend.git(null, "init", "--bare", bareRepo.toAbsolutePath().toString());

        // Create local repo and push
        Path local = tempDir.resolve("local");
        GitBackend.init(local);
        GitBackend.configureUser(local, "A", "a@test.com");
        Files.writeString(local.resolve("meta.json"), "{\"version\":1}");
        CommitPush.addAndCommit(local, "initial");
        RemoteConfig.addRemote(local, "origin", bareRepo.toAbsolutePath().toString());
        CommitPush.push(local);

        // Clone
        Path clone = tempDir.resolve("clone");
        GitBackend.cloneRepo(bareRepo.toAbsolutePath().toString(), clone);

        // Make change in local and push
        Files.writeString(local.resolve("meta.json"), "{\"version\":2}");
        CommitPush.addAndCommit(local, "update");
        CommitPush.push(local);

        // Pull in clone
        boolean conflict = PullMerge.pull(clone);
        assertFalse(conflict);
        String content = Files.readString(clone.resolve("meta.json"));
        assertTrue(content.contains("version"));
    }
}
