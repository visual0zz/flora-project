package com.flora.sanctum.storage;

import com.flora.sanctum.crypto.KeyDerivation;
import com.flora.sanctum.crypto.SecureRandomHolder;
import com.flora.sanctum.model.Entry;
import com.flora.sanctum.model.Vault;
import com.flora.sanctum.model.VaultMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VaultStoreTest {

    @TempDir
    Path tempDir;

    private byte[] deriveEncKey(char[] password, byte[] salt) {
        byte[] km = KeyDerivation.derive(password, salt, KeyDerivation.MIN_ITERATIONS);
        return KeyDerivation.encryptionKey(km);
    }

    private byte[] deriveMacKey(char[] password, byte[] salt) {
        byte[] km = KeyDerivation.derive(password, salt, KeyDerivation.MIN_ITERATIONS);
        return KeyDerivation.macKey(km);
    }

    @Test
    void createAndLoadEmptyVault() throws Exception {
        char[] password = "test-master-password".toCharArray();
        byte[] salt = new byte[16];
        SecureRandomHolder.get().nextBytes(salt);

        VaultMeta meta = new VaultMeta();
        meta.setSalt(java.util.Base64.getEncoder().encodeToString(salt));
        meta.setIterations(KeyDerivation.MIN_ITERATIONS);

        byte[] encKey = deriveEncKey(password, salt);
        byte[] macKey = deriveMacKey(password, salt);

        Vault created = VaultStore.create(tempDir.resolve("vault"), meta, macKey);
        assertTrue(created.getEntries().isEmpty());
        assertEquals(meta.getFormatVersion(), created.getMeta().getFormatVersion());

        Vault loaded = VaultStore.load(tempDir.resolve("vault"), encKey, macKey);
        assertTrue(loaded.getEntries().isEmpty());
        assertEquals(meta.getSalt(), loaded.getMeta().getSalt());
    }

    @Test
    void saveAndLoadSingleEntry() throws Exception {
        char[] password = "test-password".toCharArray();
        byte[] salt = new byte[16];
        SecureRandomHolder.get().nextBytes(salt);

        VaultMeta meta = new VaultMeta();
        meta.setSalt(java.util.Base64.getEncoder().encodeToString(salt));
        meta.setIterations(KeyDerivation.MIN_ITERATIONS);

        byte[] encKey = deriveEncKey(password, salt);
        byte[] macKey = deriveMacKey(password, salt);

        Path vaultDir = tempDir.resolve("vault");
        VaultStore.create(vaultDir, meta, macKey);

        Entry entry = new Entry("GitHub", "user@github.com", "ghp_secret",
                "https://github.com", "Dev", "My GitHub account");
        VaultStore.saveEntry(vaultDir, entry, encKey);

        Vault loaded = VaultStore.load(vaultDir, encKey, macKey);
        assertEquals(1, loaded.getEntries().size());
        assertEquals("GitHub", loaded.getEntries().get(0).getTitle());
        assertEquals("ghp_secret", loaded.getEntries().get(0).getPassword());
    }

    @Test
    void saveAndLoadMultipleEntries() throws Exception {
        char[] password = "test-password".toCharArray();
        byte[] salt = new byte[16];
        SecureRandomHolder.get().nextBytes(salt);

        VaultMeta meta = new VaultMeta();
        meta.setSalt(java.util.Base64.getEncoder().encodeToString(salt));
        meta.setIterations(KeyDerivation.MIN_ITERATIONS);

        byte[] encKey = deriveEncKey(password, salt);
        byte[] macKey = deriveMacKey(password, salt);

        Path vaultDir = tempDir.resolve("vault");
        VaultStore.create(vaultDir, meta, macKey);

        Entry e1 = new Entry("Site A", "user1", "pass1", null, null, null);
        Entry e2 = new Entry("Site B", "user2", "pass2", null, null, null);
        VaultStore.saveEntry(vaultDir, e1, encKey);
        VaultStore.saveEntry(vaultDir, e2, encKey);

        Vault loaded = VaultStore.load(vaultDir, encKey, macKey);
        assertEquals(2, loaded.getEntries().size());
    }

    @Test
    void deleteEntry() throws Exception {
        char[] password = "test-password".toCharArray();
        byte[] salt = new byte[16];
        SecureRandomHolder.get().nextBytes(salt);

        VaultMeta meta = new VaultMeta();
        meta.setSalt(java.util.Base64.getEncoder().encodeToString(salt));
        meta.setIterations(KeyDerivation.MIN_ITERATIONS);

        byte[] encKey = deriveEncKey(password, salt);
        byte[] macKey = deriveMacKey(password, salt);

        Path vaultDir = tempDir.resolve("vault");
        VaultStore.create(vaultDir, meta, macKey);

        Entry entry = new Entry("To Delete", "user", "pass", null, null, null);
        VaultStore.saveEntry(vaultDir, entry, encKey);

        VaultStore.deleteEntry(vaultDir, entry.getId());

        Vault loaded = VaultStore.load(vaultDir, encKey, macKey);
        assertTrue(loaded.getEntries().isEmpty());
    }

    @Test
    void wrongEncryptionKeyFails() throws Exception {
        char[] password = "correct-password".toCharArray();
        byte[] salt = new byte[16];
        SecureRandomHolder.get().nextBytes(salt);

        VaultMeta meta = new VaultMeta();
        meta.setSalt(java.util.Base64.getEncoder().encodeToString(salt));
        meta.setIterations(KeyDerivation.MIN_ITERATIONS);

        byte[] encKey = deriveEncKey(password, salt);
        byte[] macKey = deriveMacKey(password, salt);

        Path vaultDir = tempDir.resolve("vault");
        VaultStore.create(vaultDir, meta, macKey);

        Entry entry = new Entry("Secret", "u", "p", null, null, null);
        VaultStore.saveEntry(vaultDir, entry, encKey);

        // Try loading with wrong encryption key
        byte[] wrongSalt = new byte[16];
        SecureRandomHolder.get().nextBytes(wrongSalt);
        byte[] wrongEncKey = deriveEncKey("wrong-password".toCharArray(), wrongSalt);

        assertThrows(Exception.class, () -> VaultStore.load(vaultDir, wrongEncKey, macKey));
    }

    @Test
    void tamperedMetaJsonDetected() throws Exception {
        char[] password = "test-password".toCharArray();
        byte[] salt = new byte[16];
        SecureRandomHolder.get().nextBytes(salt);

        VaultMeta meta = new VaultMeta();
        meta.setSalt(java.util.Base64.getEncoder().encodeToString(salt));
        meta.setIterations(KeyDerivation.MIN_ITERATIONS);

        byte[] encKey = deriveEncKey(password, salt);
        byte[] macKey = deriveMacKey(password, salt);

        Path vaultDir = tempDir.resolve("vault");
        VaultStore.create(vaultDir, meta, macKey);

        // Tamper with meta.json
        java.nio.file.Files.writeString(vaultDir.resolve("meta.json"),
                "{\"formatVersion\":1,\"remotes\":[{\"url\":\"https://evil.com/vault.git\"}]}",
                java.nio.charset.StandardCharsets.UTF_8);

        assertThrows(Exception.class, () -> VaultStore.load(vaultDir, encKey, macKey));
    }
}
