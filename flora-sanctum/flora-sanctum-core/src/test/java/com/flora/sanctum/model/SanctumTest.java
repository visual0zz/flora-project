package com.flora.sanctum.model;

import com.flora.root.codec.json.model.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("osmetes:secret") // 测试假密钥
class SanctumTest {

    @TempDir
    Path dir;

    @Test
    void createEntryAndReadBack() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("username", "alice");
        fields.put("password", "s3cret");
        UUID entryUuid = s.createEntry(null, "微博", fields);

        JsonObject entry = s.getEntry(entryUuid);
        assertNotNull(entry);
        assertEquals("entry", entry.getString("type"));
        assertEquals("微博", entry.getString("name"));

        // 字段应作为独立对象存在
        UUID fieldUuid = s.directory().childrenOf(entryUuid).stream()
                .filter(u -> !u.equals(entryUuid)).findFirst().orElseThrow();
        JsonObject field = s.getEntry(fieldUuid);
        assertEquals("field", field.getString("type"));
    }

    @Test
    void deleteEntryRemovesFields() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("password", "x");
        UUID entryUuid = s.createEntry(null, "条目", fields);
        int before = s.directory().childrenOf(entryUuid).size();
        assertTrue(before > 0);

        s.deleteEntry(entryUuid);
        assertNull(s.getEntry(entryUuid));
    }

    @Test
    void deleteFieldRemovesOnlyThatField() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("username", "alice");
        fields.put("password", "s3cret");
        UUID entryUuid = s.createEntry(null, "条目", fields);
        UUID fieldUuid = s.directory().childrenOf(entryUuid).stream()
                .filter(u -> {
                    JsonObject f = s.getEntry(u);
                    return f != null && "username".equals(f.getString("fieldName"));
                })
                .findFirst().orElseThrow();

        s.deleteField(fieldUuid);
        assertNull(s.getEntry(fieldUuid));
        // 其它字段仍在，条目仍在
        assertNotNull(s.getEntry(entryUuid));
        assertTrue(s.directory().childrenOf(entryUuid).stream()
                .anyMatch(u -> {
                    JsonObject f = s.getEntry(u);
                    return f != null && "password".equals(f.getString("fieldName"));
                }));
    }

    @Test
    void renameEntryPersistsName() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID entryUuid = s.createEntry(null, "旧名", Map.of("k", "v"));

        s.renameEntry(entryUuid, "新名");
        assertEquals("新名", s.getEntry(entryUuid).getString("name"));

        // 重开重解锁后名称仍保留
        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock("pw".toCharArray());
        assertEquals("新名", s2.getEntry(entryUuid).getString("name"));
    }

    @Test
    void setEntryIconAssignsAndClears() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID entryUuid = s.createEntry(null, "条目", Map.of("k", "v"));
        UUID iconUuid = s.createIcon(new byte[]{1, 2, 3}, "png");

        s.setEntryIcon(entryUuid, iconUuid);
        assertEquals(iconUuid.toString(), s.getEntry(entryUuid).getString("icon"));

        s.setEntryIcon(entryUuid, null);
        assertNull(s.getEntry(entryUuid).getString("icon"));
    }

    @Test
    void createIconAndSshKeyAndRemote() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID iconUuid = s.createIcon(new byte[]{9, 9}, "png");
        assertNotNull(s.getEntry(iconUuid));
        assertEquals("icon", s.getEntry(iconUuid).getString("type"));

        UUID keyUuid = s.createSshKey("mykey", "-----BEGIN OPENSSH PRIVATE KEY-----");
        assertNotNull(s.getEntry(keyUuid));
        assertEquals("sshKey", s.getEntry(keyUuid).getString("type"));

        UUID remoteUuid = s.createRemote("origin", "git@example.com:repo.git", "mykey");
        assertNotNull(s.getEntry(remoteUuid));
        assertEquals("field", s.getEntry(remoteUuid).getString("type"));
        assertEquals("remote", s.getEntry(remoteUuid).getString("kind"));
    }

    @Test
    void lockAndReunlock() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID entryUuid = s.createEntry(null, "条目", Map.of("k", "v"));
        s.lock();
        assertFalse(s.isUnlocked());
        s.unlock("pw".toCharArray());
        assertTrue(s.isUnlocked());
        assertNotNull(s.getEntry(entryUuid));
    }

    @Test
    void closeUpdatesWarehouseTimeAndReunlock() {
        char[] pw = "pw".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, pw);
        long wtBefore = s.vault().clock().warehouseTime();
        s.close(); // 更新 warehouseTime + 重写 manifest + 锁定
        assertFalse(s.isUnlocked());

        // 重新打开解锁，应能读到新 manifest
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(pw);
        assertTrue(s2.isUnlocked());
        assertTrue(s2.vault().manifest().warehouseTime() >= wtBefore);
    }

    @Test
    void entryInSubGroupUsesFolderDekAndSurvivesRelock() {
        char[] pw = "pw".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, pw);
        UUID group = s.createGroup(null, "社交");
        UUID entry = s.createEntry(group, "微博", Map.of("password", "s3cret"));

        // 锁定并重开，文件夹 DEK 树递归发现
        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(pw);
        assertTrue(s2.isUnlocked());
        assertNotNull(s2.folderDek(group));
        JsonObject e = s2.getEntry(entry);
        assertNotNull(e);
        assertEquals("微博", e.getString("name"));
    }

    @Test
    void changeMasterPassword() {
        char[] oldPw = "old".toCharArray();
        char[] newPw = "new-pass".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, oldPw);
        UUID entry = s.createEntry(null, "条目", Map.of("k", "v"));

        s.changeMasterPassword(newPw, 65536, 3, 4);
        s.close();

        // 新密码可解锁
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(newPw);
        assertTrue(s2.isUnlocked());
        assertNotNull(s2.getEntry(entry));
        s2.close();

        // 旧密码不可解锁
        Sanctum s3 = Sanctum.open(dir);
        assertThrows(IllegalArgumentException.class, () -> s3.unlock(oldPw));
    }

    @Test
    void gcKeepsReachableObjects() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID group = s.createGroup(null, "社交");
        UUID entry = s.createEntry(group, "微博", Map.of("password", "s3cret"));

        // 正常对象都在可达树内，不应被 GC 误删
        java.util.List<UUID> orphaned = s.collectGarbage();
        assertFalse(orphaned.contains(entry), "reachable entry should survive GC");
        // 条目仍可读
        assertNotNull(s.getEntry(entry));
    }

    @Test
    void createIconAndSshKey() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID icon = s.createIcon(new byte[]{1, 2, 3}, "png");
        UUID ssh = s.createSshKey("mykey", "-----BEGIN RSA PRIVATE KEY-----\n...");

        JsonObject iconNode = s.getEntry(icon);
        JsonObject sshNode = s.getEntry(ssh);
        assertNotNull(iconNode);
        assertEquals("icon", iconNode.getString("type"));
        assertNotNull(sshNode);
        assertEquals("sshKey", sshNode.getString("type"));
        assertEquals("mykey", sshNode.getString("name"));
    }

    @Test
    void rootParentsUseConceptTags() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        // 顶层 group / entry → parent 为根概念 data
        UUID group = s.createGroup(null, "社交");
        UUID entry = s.createEntry(null, "顶层条目", Map.of("password", "x"));
        assertEquals("data", s.getEntry(group).getString("parent"));
        assertEquals("data", s.getEntry(entry).getString("parent"));

        // remote → parent 为根概念 remote
        UUID remote = s.createRemote("origin", "git@example.com:r.git", null);
        assertEquals("remote", s.getEntry(remote).getString("parent"));

        // icon / sshKey → parent 为对应 root group uuid
        UUID icon = s.createIcon(new byte[]{1}, "png");
        UUID ssh = s.createSshKey("k", "-----BEGIN PRIVATE KEY-----");
        assertEquals(s.vault().rootGroupUuid(RootTag.ICON).toString(), s.getEntry(icon).getString("parent"));
        assertEquals(s.vault().rootGroupUuid(RootTag.SSH_KEY).toString(), s.getEntry(ssh).getString("parent"));

        // 三个 root group 的 parent 是概念 tag；manifest 明文块 parent=manifest
        // （root group 块用 KEK 加密，getEntry 走 keyIdIndex 不含 KEK，故用 vault.resolve 直接解块验证）
        java.util.UUID dataRoot = s.vault().rootGroupUuid(RootTag.DATA);
        java.util.UUID iconRoot = s.vault().rootGroupUuid(RootTag.ICON);
        java.util.UUID sshRoot = s.vault().rootGroupUuid(RootTag.SSH_KEY);
        assertNotNull(dataRoot);
        assertNotNull(iconRoot);
        assertNotNull(sshRoot);
        assertEquals("data", rootParent(s, dataRoot));
        assertEquals("icon", rootParent(s, iconRoot));
        assertEquals("sshKey", rootParent(s, sshRoot));
        assertEquals("manifest", s.vault().manifest().parent());

        // 重开重解锁后概念 tag 结构与 root group 登记仍正确
        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock("pw".toCharArray());
        assertEquals("data", s2.getEntry(group).getString("parent"));
        assertEquals("data", s2.getEntry(entry).getString("parent"));
        assertEquals("remote", s2.getEntry(remote).getString("parent"));
        assertEquals(s2.vault().rootGroupUuid(RootTag.ICON).toString(), s2.getEntry(icon).getString("parent"));
        assertEquals("manifest", s2.vault().manifest().parent());
    }

    /** 直接解某个 root group 块的负载 parent（root group 块用 KEK 加密，getEntry/BlockResolver 不可读）。 */
    private static String rootParent(Sanctum s, java.util.UUID uuid) {
        byte[] kek = s.vault().kek();
        for (com.flora.sanctum.store.Block b : s.store().scan()) {
            if (b.uuid().equals(uuid)) {
                byte[] encKey = com.flora.sanctum.crypto.KeyDerivation.encKey(kek);
                com.flora.sanctum.crypto.impl.CipherCodec codec =
                        new com.flora.sanctum.crypto.impl.CipherCodec(encKey, kek, s.vault().random());
                byte[] plain = codec.decode(b.obfuscated()).plaintext;
                JsonObject n = com.flora.root.codec.JsonUtil.parseObject(
                        new String(plain, java.nio.charset.StandardCharsets.UTF_8));
                return n.getString("parent");
            }
        }
        return null;
    }

    @Test
    void guiFlow_groupEntryFieldUpdate() {
        // 模拟 GUI 核心操作链：建组 → 建条目 → 建字段 → 更新字段 → 按组列出条目
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID group = s.createGroup(null, "社交");
        UUID entry = s.createEntry(group, "微博", java.util.Map.of("username", "alice", "password", "s3cret"));

        // 更新某字段值
        java.util.List<UUID> fieldUuids = s.directory().childrenOf(entry);
        UUID passwordField = null;
        for (UUID f : fieldUuids) {
            JsonObject n = s.getEntry(f);
            if (n != null && "password".equals(n.getString("fieldName"))) {
                passwordField = f;
            }
        }
        assertNotNull(passwordField);
        s.updateField(passwordField, "new-password");
        JsonObject updated = s.getEntry(passwordField);
        assertEquals("new-password", updated.getString("value"));

        // 按组列出条目（GUI 主界面逻辑）
        boolean entryInGroup = false;
        for (UUID u : s.listObjectUuids()) {
            JsonObject n = s.getEntry(u);
            if (n != null && "entry".equals(n.getString("type"))
                    && group.toString().equals(n.getString("parent"))) {
                entryInGroup = true;
            }
        }
        assertTrue(entryInGroup);
    }
}
