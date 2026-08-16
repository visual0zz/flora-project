package com.flora.sanctum.model;

import com.flora.root.codec.json.model.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("osmetes:secret") // 测试假密钥
class SanctumTest {

    @TempDir
    Path dir;

    @Test
    void createEntryAndReadBack() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID entryUuid = s.createEntry(null, "微博",
                new EntryFields("s3cret", null, "alice", List.of()));

        JsonObject entry = s.getEntry(entryUuid);
        assertNotNull(entry);
        assertEquals("entry", entry.getString("type"));
        assertEquals("微博", entry.getString("name"));
        // 内置字段直接读得到
        assertEquals("alice", entry.getString("username"));
        assertEquals("s3cret", entry.getString("password"));
        assertNotNull(entry.getLong("createTime"));
        assertNotNull(entry.getLong("updateTime"));
    }

    @Test
    void deleteEntryRemovesBuiltin() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID entryUuid = s.createEntry(null, "条目",
                new EntryFields("x", null, null, List.of()));
        assertNotNull(s.getEntry(entryUuid).getString("password"));

        s.deleteEntry(entryUuid);
        assertNull(s.getEntry(entryUuid));
    }

    @Test
    void deleteCustomFieldPreservesBuiltinPassword() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID entryUuid = s.createEntry(null, "条目",
                new EntryFields("s3cret", null, "alice", List.of()));
        // 创建自定义字段（notes），模拟旧"密码+用户名"独立块 → 现在用 createFieldWithKind
        UUID noteField = s.createFieldWithKind(entryUuid, null, "notes", "memo", null);

        s.deleteField(noteField);
        assertNull(s.getEntry(noteField));
        // entry 与内置密码仍在
        assertNotNull(s.getEntry(entryUuid));
        assertEquals("s3cret", s.getEntry(entryUuid).getString("password"));
    }

    @Test
    void renameEntryPersistsName() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID entryUuid = s.createEntry(null, "旧名", EntryFields.EMPTY);

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
        UUID entryUuid = s.createEntry(null, "条目", EntryFields.EMPTY);
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
        UUID entryUuid = s.createEntry(null, "条目", EntryFields.EMPTY);
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
        s.close();
        assertFalse(s.isUnlocked());

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
        UUID entry = s.createEntry(group, "微博",
                new EntryFields("s3cret", null, null, List.of()));

        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(pw);
        assertTrue(s2.isUnlocked());
        assertNotNull(s2.folderDek(group));
        JsonObject e = s2.getEntry(entry);
        assertNotNull(e);
        assertEquals("微博", e.getString("name"));
        assertEquals("s3cret", e.getString("password"));
    }

    @Test
    void changeMasterPassword() {
        char[] oldPw = "old".toCharArray();
        char[] newPw = "new-pass".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, oldPw);
        UUID entry = s.createEntry(null, "条目", EntryFields.EMPTY);

        s.changeMasterPassword(newPw, 65536, 3, 4);
        s.close();

        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(newPw);
        assertTrue(s2.isUnlocked());
        assertNotNull(s2.getEntry(entry));
        s2.close();

        Sanctum s3 = Sanctum.open(dir);
        assertThrows(IllegalArgumentException.class, () -> s3.unlock(oldPw));
    }

    @Test
    void gcKeepsReachableObjects() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID group = s.createGroup(null, "社交");
        UUID entry = s.createEntry(group, "微博",
                new EntryFields("s3cret", null, null, List.of()));

        java.util.List<UUID> orphaned = s.collectGarbage();
        assertFalse(orphaned.contains(entry), "reachable entry should survive GC");
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
        UUID group = s.createGroup(null, "社交");
        UUID entry = s.createEntry(null, "顶层条目",
                new EntryFields("x", null, null, List.of()));
        assertEquals("data", s.getEntry(group).getString("parent"));
        assertEquals("data", s.getEntry(entry).getString("parent"));

        UUID remote = s.createRemote("origin", "git@example.com:r.git", null);
        assertEquals("remote", s.getEntry(remote).getString("parent"));

        UUID icon = s.createIcon(new byte[]{1}, "png");
        UUID ssh = s.createSshKey("k", "-----BEGIN PRIVATE KEY-----");
        assertEquals(s.vault().rootGroupUuid(RootTag.ICON).toString(), s.getEntry(icon).getString("parent"));
        assertEquals(s.vault().rootGroupUuid(RootTag.SSH_KEY).toString(), s.getEntry(ssh).getString("parent"));

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

        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock("pw".toCharArray());
        assertEquals("data", s2.getEntry(group).getString("parent"));
        assertEquals("data", s2.getEntry(entry).getString("parent"));
        assertEquals("remote", s2.getEntry(remote).getString("parent"));
        assertEquals(s2.vault().rootGroupUuid(RootTag.ICON).toString(), s2.getEntry(icon).getString("parent"));
        assertEquals("manifest", s2.vault().manifest().parent());
    }

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
    void renameGroupRenamesFolder() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID group = s.createGroup(null, "旧名");
        s.renameGroup(group, "新名");
        assertEquals("新名", s.getEntry(group).getString("name"));

        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock("pw".toCharArray());
        assertEquals("新名", s2.getEntry(group).getString("name"));
    }

    @Test
    void updateFieldKindChangesKind() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID entry = s.createEntry(null, "条目", EntryFields.EMPTY);
        // 创建一个自定义字段（username 现在是内置，不再是独立块）
        UUID field = s.createFieldWithKind(entry, null, "notes", "memo", null);
        assertNull(s.getEntry(field).getString("kind"));
        s.updateFieldKind(field, "totp");
        assertEquals("totp", s.getEntry(field).getString("kind"));

        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock("pw".toCharArray());
        assertEquals("totp", s2.getEntry(field).getString("kind"));
    }

    @Test
    void guiFlow_groupEntryFieldUpdate() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        UUID group = s.createGroup(null, "社交");
        UUID entry = s.createEntry(group, "微博",
                new EntryFields("s3cret", null, "alice", List.of()));

        // 更新内置密码
        s.updateEntryBuiltins(entry, new EntryFields("new-password", null, "alice", List.of()));
        assertEquals("new-password", s.getEntry(entry).getString("password"));

        // 创建并更新自定义字段
        UUID customField = s.createFieldWithKind(entry, group, "url", "https://x", null);
        s.updateField(customField, "https://updated");
        assertEquals("https://updated", s.getEntry(customField).getString("value"));

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

    @Test
    void entryHasBuiltinPasswordUrlUsernameLabels() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        long before = System.currentTimeMillis();
        UUID entryUuid = s.createEntry(null, "账号",
                new EntryFields("p@ss", "https://example.com", "alice", List.of("work", "important")));
        long after = System.currentTimeMillis();

        JsonObject entry = s.getEntry(entryUuid);
        assertEquals("p@ss", entry.getString("password"));
        assertEquals("https://example.com", entry.getString("url"));
        assertEquals("alice", entry.getString("username"));
        assertEquals(List.of("work", "important"), com.flora.sanctum.model.EntryFields.labelsOf(entry));

        long ct = entry.getLong("createTime");
        assertTrue(ct >= before && ct <= after);
        assertEquals(ct, entry.getLong("updateTime"));

        // 更新内置字段，updateTime 应改变
        s.updateEntryBuiltins(entryUuid, new EntryFields("p@ss2", "https://example.com", "alice", List.of()));
        long updateTime2 = s.getEntry(entryUuid).getLong("updateTime");
        assertTrue(updateTime2 >= ct);
    }
}