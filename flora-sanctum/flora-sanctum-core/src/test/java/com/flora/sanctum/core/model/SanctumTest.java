package com.flora.sanctum.core.model;
import com.flora.sanctum.core.model.tree.*;
import com.flora.sanctum.core.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("osmetes:secret") // 测试假密钥
class SanctumTest {

    @TempDir
    Path dir;

    @Test
    void createEntryAndReadBack() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        EntryNode entry = s.objectTree().createEntry(null, "微博",
                new EntryFields("s3cret", null, "alice", List.of()));

        assertEquals(StoredNodeType.ENTRY, entry.type());
        assertEquals("微博", entry.name());
        assertEquals("alice", entry.username());
        assertEquals("s3cret", entry.password());
        assertNotNull(entry.createTime());
        assertNotNull(entry.updateTime());
    }

    @Test
    void deleteEntryRemovesBuiltin() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        EntryNode entry = s.objectTree().createEntry(null, "条目",
                new EntryFields("x", null, null, List.of()));
        assertNotNull(entry.password());

        entry.delete();
        assertNull(s.objectTree().entry(entry.uuid()));
    }

    @Test
    void deleteCustomFieldPreservesBuiltinPassword() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        EntryNode entry = s.objectTree().createEntry(null, "条目",
                new EntryFields("s3cret", null, "alice", List.of()));
        FieldNode note = entry.writeField("memo", "memo", null);

        note.delete();
        assertNull(s.objectTree().field(note.uuid()));
        assertNotNull(s.objectTree().entry(entry.uuid()));
        assertEquals("s3cret", s.objectTree().entry(entry.uuid()).password());
    }

    @Test
    void renameEntryPersistsName() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        EntryNode entry = s.objectTree().createEntry(null, "旧名", EntryFields.EMPTY);

        entry.rename("新名");
        assertEquals("新名", s.objectTree().entry(entry.uuid()).name());

        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock("pw".toCharArray());
        assertEquals("新名", s2.objectTree().entry(entry.uuid()).name());
    }

    @Test
    void setEntryIconAssignsAndClears() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        EntryNode entry = s.objectTree().createEntry(null, "条目", EntryFields.EMPTY);
        IconNode icon = s.iconTree().createIcon("icon", new byte[]{1, 2, 3}, "png");

        entry.setIcon(icon.uuid());
        assertEquals(Ref.nodeIcon(icon.uuid()), s.objectTree().entry(entry.uuid()).iconRef());

        entry.setIcon((UUID) null);
        assertNull(s.objectTree().entry(entry.uuid()).iconRef());
    }

    @Test
    void createIconAndSshKeyAndRemote() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        IconNode icon = s.iconTree().createIcon("icon", new byte[]{9, 9}, "png");
        assertNotNull(s.iconTree().find(icon.uuid()));
        assertEquals("icon", s.iconTree().find(icon.uuid()).name());

        SshKeyNode key = s.sshKeyTree().createSshKey("mykey", "-----BEGIN OPENSSH PRIVATE KEY-----");
        assertNotNull(s.sshKeyTree().find(key.uuid()));
        assertEquals(StoredNodeType.SSH_KEY, key.type());

        RemoteNode remote = s.remoteTree().addRemote("origin", "git@example.com:repo.git", Ref.nodeKey(key.uuid()));
        assertNotNull(s.remoteTree().find(remote.uuid()));
        assertEquals(StoredNodeType.REMOTE, remote.type());
        assertEquals(Ref.nodeKey(key.uuid()), remote.keyRef());
    }

    @Test
    void lockAndReunlock() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        EntryNode entry = s.objectTree().createEntry(null, "条目", EntryFields.EMPTY);
        s.lock();
        assertFalse(s.isUnlocked());
        s.unlock("pw".toCharArray());
        assertTrue(s.isUnlocked());
        assertNotNull(s.objectTree().entry(entry.uuid()));
    }

    @Test
    void repoKeyIdSeedPersistsAcrossReunlock() {
        char[] pw = "pw".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, pw, 8192, 2, 1);
        byte[] seed1 = s.vault().repoKeyIdSeed();
        assertNotNull(seed1);
        assertEquals(32, seed1.length);
        s.close();

        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(pw);
        byte[] seed2 = s2.vault().repoKeyIdSeed();
        assertNotNull(seed2);
        assertArrayEquals(seed1, seed2);
        s2.close();
    }

    @Test
    void closeLocksAndReunlockWorks() {
        char[] pw = "pw".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, pw, 8192, 2, 1);
        long anchor = s.vault().clock().baseTimestamp();
        s.close();
        assertFalse(s.isUnlocked());

        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(pw);
        assertTrue(s2.isUnlocked());
        // 会话时钟锚点 = 全库块最大时间戳（解锁时扫描），与关闭前一致
        assertTrue(s2.vault().clock().baseTimestamp() >= anchor);
    }

    @Test
    void entryInSubGroupUsesFolderDekAndSurvivesRelock() {
        char[] pw = "pw".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, pw, 8192, 2, 1);
        GroupNode group = s.objectTree().createGroup(null, "社交");
        EntryNode entry = group.createEntry("微博", new EntryFields("s3cret", null, null, List.of()));

        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(pw);
        assertTrue(s2.isUnlocked());
        assertNotNull(s2.groupDek(group.uuid()));
        EntryNode e = s2.objectTree().entry(entry.uuid());
        assertNotNull(e);
        assertEquals("微博", e.name());
        assertEquals("s3cret", e.password());
    }

    @Test
    void moveGroupReparentAndSurvivesRelockAfterOldParentGone() {
        char[] pw = "pw".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, pw, 8192, 2, 1);
        GroupNode a = s.objectTree().createGroup(null, "A");
        GroupNode b = s.objectTree().createGroup(null, "B");
        GroupNode c = a.createChildGroup("C");
        EntryNode e = c.createEntry("条目", new EntryFields("pw", null, "u", List.of()));
        e.writeField("memo", "v", null);

        // 把 C（含条目与字段）从 A 移到 B
        s.move(c.uuid(), b.uuid());

        // 旧父 A 删除并 GC，模拟旧父 DEK 离开索引
        a.delete();
        s.collectGarbage();

        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(pw);
        GroupNode c2 = s2.objectTree().group(c.uuid());
        assertNotNull(c2);
        assertEquals(b.uuid(), com.flora.sanctum.core.util.UuidHex.fromHex(c2.parentRef()));
        EntryNode e2 = c2.entries().get(0);
        assertEquals("pw", e2.password());
        assertEquals("u", e2.username());
        assertEquals(1, e2.fields().size());
        assertEquals("v", e2.fields().get(0).value());
        // 移动后的子树不应被判为不可解锁
        assertTrue(s2.trash().unlockable().stream().noneMatch(u -> u.equals(c.uuid()) || u.equals(e.uuid())),
                "重路由后子树不应不可解锁");
    }

    @Test
    void moveEntryReparentAndSurvivesRelock() {
        char[] pw = "pw".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, pw, 8192, 2, 1);
        GroupNode a = s.objectTree().createGroup(null, "A");
        GroupNode b = s.objectTree().createGroup(null, "B");
        EntryNode e = a.createEntry("条目", new EntryFields("pw", null, "u", List.of()));
        e.writeField("memo", "v", null);

        s.move(e.uuid(), b.uuid());

        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(pw);
        EntryNode e2 = s2.objectTree().entry(e.uuid());
        assertNotNull(e2);
        assertEquals(b.uuid(), com.flora.sanctum.core.util.UuidHex.fromHex(e2.parentRef()));
        assertEquals("pw", e2.password());
        assertEquals(1, e2.fields().size());
        assertEquals("v", e2.fields().get(0).value());
    }

    @Test
    void moveRejectsMovingIntoOwnSubtree() {
        char[] pw = "pw".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, pw, 8192, 2, 1);
        GroupNode a = s.objectTree().createGroup(null, "A");
        GroupNode c = a.createChildGroup("C");

        // 把 A 移到其子孙 C 之下应被环检测拒绝
        assertThrows(IllegalArgumentException.class, () -> s.move(a.uuid(), c.uuid()));
        // 移动到自身也应被拒绝
        assertThrows(IllegalArgumentException.class, () -> s.move(a.uuid(), a.uuid()));
    }

    @Test
    void changeMasterPassword() {
        char[] oldPw = "old".toCharArray();
        char[] newPw = "new-pass".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, oldPw, 8192, 2, 1);
        EntryNode entry = s.objectTree().createEntry(null, "条目", EntryFields.EMPTY);

        s.changeMasterPassword(newPw, 65536, 3, 4);
        s.close();

        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(newPw);
        assertTrue(s2.isUnlocked());
        assertNotNull(s2.objectTree().entry(entry.uuid()));
        s2.close();

        Sanctum s3 = Sanctum.open(dir);
        com.flora.sanctum.core.model.vault.VaultUnlockException ex =
                assertThrows(com.flora.sanctum.core.model.vault.VaultUnlockException.class, () -> s3.unlock(oldPw));
        assertEquals(com.flora.sanctum.core.model.vault.VaultUnlockException.Phase.MANIFEST_CORRUPT, ex.phase());
    }

    @Test
    void changeMasterPasswordMigratesRootLevelGroupDek() {
        char[] oldPw = "old".toCharArray();
        char[] newPw = "new-pass".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, oldPw, 8192, 2, 1);
        // 顶层分组的 DEK 由根级密钥（= KEK）包裹：换密码后必须重新包裹才能解开
        GroupNode group = s.objectTree().createGroup(null, "社交");
        EntryNode entry = group.createEntry("微博", new EntryFields("s3cret", null, null, List.of()));

        s.changeMasterPassword(newPw, 65536, 3, 4);
        s.close();

        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(newPw);
        assertTrue(s2.isUnlocked());
        assertNotNull(s2.groupDek(group.uuid()), "顶层分组 DEK 应改用新 KEK 包裹并可解开");
        EntryNode e = s2.objectTree().entry(entry.uuid());
        assertNotNull(e, "分组内条目在换密码后应仍可读");
        assertEquals("s3cret", e.password());
        s2.close();
    }

    @Test
    void relocatedBlockFailsAuthentication() throws Exception {
        char[] pw = "pw".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, pw, 8192, 2, 1);
        UUID entryId = s.objectTree().createEntry(null, "条目", EntryFields.EMPTY).uuid();
        s.close();

        // 对象 uuid 由块文件路径承载：把文件改名为另一个合法分片名，反推出的 uuid 即改变
        String hex = entryId.toString().replace("-", "");
        Path blockFile = dir.resolve(hex.substring(0, 1)).resolve(hex.substring(1, 2))
                .resolve(hex.substring(2) + ".md");
        assertTrue(Files.exists(blockFile), "条目块文件应存在");
        String rest = hex.substring(2);
        Files.move(blockFile, blockFile.resolveSibling(
                (rest.charAt(0) == 'f' ? "a" : "f") + rest.substring(1) + ".md"));

        // uuid 参与 AAD 且不写入块内：路径一变 ⇒ AAD 不一致 ⇒ 该块无法解密
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(pw);
        assertNull(s2.objectTree().entry(entryId), "被移动到别处的块应因 AAD 不一致而无法解密");
        s2.close();
    }

    @Test
    void gcKeepsReachableObjects() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode group = s.objectTree().createGroup(null, "社交");
        EntryNode entry = group.createEntry("微博", new EntryFields("s3cret", null, null, List.of()));

        java.util.List<UUID> orphaned = s.collectGarbage();
        assertFalse(orphaned.contains(entry.uuid()), "reachable entry should survive GC");
        assertNotNull(s.objectTree().entry(entry.uuid()));
    }

    @Test
    void rootParentsUseRootUuid() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode group = s.objectTree().createGroup(null, "社交");
        EntryNode entry = s.objectTree().createEntry(null, "顶层条目",
                new EntryFields("x", null, null, List.of()));
        String root = com.flora.sanctum.core.util.UuidHex.toHex(s.rootObjectUuid());
        assertEquals(root, group.parentRef());
        assertEquals(root, entry.parentRef());

        RemoteNode remote = s.remoteTree().addRemote("origin", "git@example.com:r.git", null);
        assertEquals(root, remote.parentRef());

        IconNode icon = s.iconTree().createIcon("icon", new byte[]{1}, "png");
        SshKeyNode ssh = s.sshKeyTree().createSshKey("k", "-----BEGIN PRIVATE KEY-----");
        assertEquals(root, icon.parentRef());
        assertEquals(root, ssh.parentRef());

        // 根对象 uuid 由 KEK 单向推导（不记入 manifest），登记在 vault 上
        assertEquals(root, com.flora.sanctum.core.util.UuidHex.toHex(s.vault().rootObjectUuid()));

        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock("pw".toCharArray());
        String root2 = com.flora.sanctum.core.util.UuidHex.toHex(s2.rootObjectUuid());
        assertEquals(root2, s2.objectTree().group(group.uuid()).parentRef());
        assertEquals(root2, s2.objectTree().entry(entry.uuid()).parentRef());
        assertEquals(root2, s2.remoteTree().remote("origin").parentRef());
        assertEquals(root2, com.flora.sanctum.core.util.UuidHex.toHex(s2.vault().rootObjectUuid()));
    }

    @Test
    void renameGroupRenamesFolder() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode group = s.objectTree().createGroup(null, "旧名");
        group.rename("新名");
        assertEquals("新名", s.objectTree().group(group.uuid()).name());

        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock("pw".toCharArray());
        assertEquals("新名", s2.objectTree().group(group.uuid()).name());
    }

    @Test
    void updateFieldKindChangesKind() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        EntryNode entry = s.objectTree().createEntry(null, "条目", EntryFields.EMPTY);
        FieldNode field = entry.writeField("memo", "memo", null);
        assertNull(field.kind());
        field.updateKind("totp");
        assertEquals("totp", s.objectTree().field(field.uuid()).kind());

        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock("pw".toCharArray());
        assertEquals("totp", s2.objectTree().field(field.uuid()).kind());
    }

    @Test
    void guiFlow_groupEntryFieldUpdate() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode group = s.objectTree().createGroup(null, "社交");
        EntryNode entry = group.createEntry("微博",
                new EntryFields("s3cret", null, "alice", List.of()));

        // 更新内置密码
        entry.updateBuiltins(new EntryFields("new-password", null, "alice", List.of()));
        assertEquals("new-password", s.objectTree().entry(entry.uuid()).password());

        // 创建并更新自定义字段（预设名 url 不可用于自定义字段）
        FieldNode custom = entry.writeField("website", "https://x", null);
        custom.updateValue("https://updated");
        assertEquals("https://updated", s.objectTree().field(custom.uuid()).value());

        // 按组列出条目
        assertEquals(1, group.entries().size());
    }

    @Test
    void entryHasBuiltinPasswordUrlUsernameLabels() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        long before = System.currentTimeMillis();
        EntryNode entry = s.objectTree().createEntry(null, "账号",
                new EntryFields("p@ss", "https://example.com", "alice", List.of("work", "important")));
        long after = System.currentTimeMillis();

        assertEquals("p@ss", entry.password());
        assertEquals("https://example.com", entry.url());
        assertEquals("alice", entry.username());
        assertEquals(List.of("work", "important"), entry.labels());

        long ct = entry.createTime();
        assertTrue(ct >= before && ct <= after);
        // updateTime = max(条目, 全部字段)，字段块写入可能晚 1ms，故 >=
        assertTrue(entry.updateTime() >= ct);

        // 更新内置字段，updateTime 应改变
        entry.updateBuiltins(new EntryFields("p@ss2", "https://example.com", "alice", List.of()));
        long updateTime2 = s.objectTree().entry(entry.uuid()).updateTime();
        assertTrue(updateTime2 >= ct);
    }

    @Test
    void entryNotesIsBuiltinPresetField() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        EntryNode entry = s.objectTree().createEntry(null, "账号", EntryFields.EMPTY);
        assertNull(entry.notes());

        entry.setNotes("多行\n备注内容");
        assertEquals("多行\n备注内容", s.objectTree().entry(entry.uuid()).notes());

        // 清空备注应删除预设块
        entry.setNotes("");
        assertNull(s.objectTree().entry(entry.uuid()).notes());

        // 备注与密码等内置字段相互独立
        entry.setNotes("私有备注");
        entry.updateBuiltins(new EntryFields("p@ss", null, "alice", List.of()));
        EntryNode reread = s.objectTree().entry(entry.uuid());
        assertEquals("私有备注", reread.notes());
        assertEquals("p@ss", reread.password());
    }

    @Test
    void groupTreeStructureAndDelete() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode root = s.objectTree().createGroup(null, "根组");
        GroupNode child = root.createChildGroup("子组");
        EntryNode entry = child.createEntry("条目", EntryFields.EMPTY);

        assertEquals(1, root.childGroups().size());
        assertEquals(1, child.entries().size());

        // 递归删除根组 → 子组与条目都被删
        root.delete();
        assertNull(s.objectTree().group(root.uuid()));
        assertNull(s.objectTree().group(child.uuid()));
        assertNull(s.objectTree().entry(entry.uuid()));
    }

    @Test
    void remoteTreeLookupAndRemove() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        SshKeyNode k = s.sshKeyTree().createSshKey("k", "-----BEGIN PRIVATE KEY-----");
        s.remoteTree().addRemote("origin", "git@example.com:r.git", Ref.nodeKey(k.uuid()));
        RemoteNode r = s.remoteTree().remote("origin");
        assertNotNull(r);
        assertEquals("git@example.com:r.git", r.url());

        s.remoteTree().removeRemote("origin");
        assertNull(s.remoteTree().remote("origin"));
    }

    @Test
    void findNodeAcrossTrees() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        EntryNode entry = s.objectTree().createEntry(null, "条目", EntryFields.EMPTY);
        IconNode icon = s.iconTree().createIcon("icon", new byte[]{1}, "png");

        assertTrue(s.findNode(entry.uuid()) instanceof EntryNode);
        assertTrue(s.findNode(icon.uuid()) instanceof IconNode);
        assertNull(s.findNode(UUID.randomUUID()));
    }

    @Test
    void nodeAndManifestCarryBlockLocation() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        EntryNode entry = s.objectTree().createEntry(null, "条目", EntryFields.EMPTY);

        // 节点应带原始块定位（文件 + 行号）
        assertNotNull(entry.block());
        assertEquals(entry.uuid(), entry.block().uuid());
        assertNotNull(entry.file());
        assertTrue(entry.lineNumber() >= 1);

        // manifest 明文块也应带定位（文件 + 行号）
        com.flora.sanctum.core.store.Block mb = new com.flora.sanctum.core.model.impl.ManifestStore(
                s.store(), new com.flora.sanctum.core.crypto.impl.SecureRandomSource()).findBlock();
        assertNotNull(mb);
        assertNotNull(mb.file());
        assertTrue(mb.line() >= 1);
    }

    @Test
    void manualDeleteMarksAndClassifies() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode group = s.objectTree().createGroup(null, "社交");
        EntryNode entry = group.createEntry("微博", EntryFields.EMPTY);

        // 手动删除组：标记写入，子节点不动
        group.markDeleted();
        assertTrue(group.deleted());
        assertFalse(entry.deleted(), "子节点不应被标记删除");
        // 子节点仍能按原 parent 找到
        assertEquals(1, group.entries().size());

        TrashView trash = s.trash();
        assertTrue(trash.manual().contains(group.uuid()));
        assertEquals(TrashView.TrashKind.MANUAL, trash.kindOf(group.uuid()));
        assertFalse(trash.contains(entry.uuid()));

        // 原位置沿 parent 链计算（core 返回中性路径段，末段为组名）
        assertTrue(trash.originalPathSegments(group.uuid()).getLast().equals("社交"));

        // 撤销删除
        group.restore();
        assertFalse(group.deleted());
        assertFalse(s.trash().manual().contains(group.uuid()));
    }

    // ---- 回归：同一 uuid 被二次写入（setIcon / rename）时索引不能重复 ----

    @Test
    void setGroupIconDoesNotDuplicateInParent() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode parent = s.objectTree().createGroup(null, "父");
        IconNode icon = s.iconTree().createIcon("icon", new byte[]{1}, "png");
        GroupNode child = parent.createChildGroup("子");
        // KdbxMapper 在其后调用 setIcon，复用同一 uuid 二次写入 group 对象
        child.setIcon(icon.uuid());
        assertEquals(1, parent.childGroups().size(), "同一 uuid 二次写入不应在父组子列表重复");
        assertEquals(child.uuid(), parent.childGroups().get(0).uuid());
    }

    @Test
    void setEntryIconDoesNotDuplicateInParent() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode parent = s.objectTree().createGroup(null, "父");
        IconNode icon = s.iconTree().createIcon("icon", new byte[]{1}, "png");
        EntryNode entry = parent.createEntry("条目", EntryFields.EMPTY);
        entry.setIcon(icon.uuid());
        assertEquals(1, parent.entries().size(), "同一 uuid 二次写入不应在父组子列表重复");
    }

    @Test
    void renameGroupDoesNotDuplicateInParent() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode parent = s.objectTree().createGroup(null, "父");
        GroupNode child = parent.createChildGroup("子");
        child.rename("新名");
        assertEquals(1, parent.childGroups().size(), "rename 复用同一 uuid 不应在父组子列表重复");
    }

    @Test
    void unreachableEntryClassified() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        EntryNode entry = s.objectTree().createEntry(null, "孤立", EntryFields.EMPTY);
        // 篡改 parent 指向不存在的 uuid（内存图直接改，classify 读内存图判定不可达）
        entry.data().put("parent", UUID.randomUUID().toString());

        TrashView trash = s.trash();
        assertTrue(trash.unreachable().contains(entry.uuid()), "篡改 parent 后应判定为不可达");
    }

    // ---- 垃圾桶：门面还原 / 彻底删除 ----

    @Test
    void facadeRestorePutsDeletedEntryBackInPlace() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode group = s.objectTree().createGroup(null, "社交");
        EntryNode entry = group.createEntry("微博", EntryFields.EMPTY);
        UUID id = entry.uuid();

        entry.markDeleted();
        assertTrue(s.trash().manual().contains(id), "删除后应进入垃圾桶");

        s.restore(id);

        assertFalse(s.trash().manual().contains(id), "还原后应移出垃圾桶");
        assertFalse(entry.deleted());
        assertTrue(group.entries().stream().anyMatch(e -> e.uuid().equals(id)),
                "还原后条目应回到原组下");
    }

    @Test
    void facadePurgeRemovesWholeSubtreeFromStore() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode parent = s.objectTree().createGroup(null, "父");
        GroupNode child = parent.createChildGroup("子");
        EntryNode e1 = parent.createEntry("条目A", EntryFields.EMPTY);
        EntryNode e2 = child.createEntry("条目B", EntryFields.EMPTY);
        e1.writeField("备注", "x", null);

        // 收集子树全部 uuid（组 + 条目 + 全部字段块，含预设字段块）
        List<UUID> descendants = new ArrayList<>();
        descendants.add(parent.uuid());
        descendants.add(child.uuid());
        descendants.add(e1.uuid());
        descendants.add(e2.uuid());
        descendants.addAll(e1.tree().context().childrenOf(e1.uuid()));
        descendants.addAll(e1.tree().context().childrenOf(e2.uuid()));
        int blocksBefore = s.store().scan().size();

        s.purge(parent.uuid());

        for (UUID u : descendants) {
            assertNull(s.findNode(u), "purge 后 " + u + " 不应再存在");
            assertNull(s.store().get(u, null), "purge 后块 " + u + " 应物理删除");
        }
        assertEquals(blocksBefore - descendants.size(), s.store().scan().size(),
                "purge 应物理删除全部后代块，不留孤儿");
        assertFalse(s.trash().contains(parent.uuid()), "purge 后不应再出现在垃圾桶");
    }
}
