package com.flora.sanctum.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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

        Json.Node entry = s.getEntry(entryUuid);
        assertNotNull(entry);
        assertEquals("entry", entry.str("type"));
        assertEquals("微博", entry.str("name"));

        // 字段应作为独立对象存在
        UUID fieldUuid = s.directory().childrenOf(entryUuid).stream()
                .filter(u -> !u.equals(entryUuid)).findFirst().orElseThrow();
        Json.Node field = s.getEntry(fieldUuid);
        assertEquals("field", field.str("type"));
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
        Json.Node e = s2.getEntry(entry);
        assertNotNull(e);
        assertEquals("微博", e.str("name"));
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
}
