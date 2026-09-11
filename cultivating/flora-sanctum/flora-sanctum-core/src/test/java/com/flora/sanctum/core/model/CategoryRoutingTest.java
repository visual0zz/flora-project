package com.flora.sanctum.core.model;
import com.flora.sanctum.core.model.tree.*;
import com.flora.sanctum.core.model.vault.Vault;

import com.flora.sanctum.core.util.UuidHex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * category 分隔层路由验证：四类顶层对象须分别挂到 password / icon / sshKey / remote 类别节点之下，
 * 而非直接挂到 root；解锁后类别 DEK 须已注册，且顶层对象经类别 DEK 加密后可解密（relock 后仍能读回）。
 */
@SuppressWarnings("osmetes:secret") // 测试假密钥
class CategoryRoutingTest {

    @TempDir
    Path dir;

    private static UUID parentUuid(Sanctum s, UUID id) {
        TreeNode n = s.objectTree().find(id);
        return n == null ? null : UuidHex.fromHex(n.parentRef());
    }

    private static UUID parentUuidOf(TreeNode n) {
        return UuidHex.fromHex(n.parentRef());
    }

    @Test
    void topLevelGroupAndEntryRouteToPasswordCategory() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        Vault v = s.vault();

        GroupNode g = s.objectTree().createGroup(null, "顶层组");
        EntryNode e = s.objectTree().createEntry(null, "顶层条目", EntryFields.EMPTY);

        UUID passwordCat = v.categoryUuid("password");
        assertNotNull(passwordCat);
        assertEquals(passwordCat, parentUuid(s, g.uuid()));
        assertEquals(passwordCat, parentUuid(s, e.uuid()));
        // 顶层对象的 parent 必须指向 category，而非 root 对象
        assertNotEquals(v.rootObjectUuid(), parentUuid(s, g.uuid()));
        // 类别 DEK 已注册，且顶层对象经其加密可解密
        assertNotNull(v.groupDek(passwordCat));
        assertNotNull(s.objectTree().group(g.uuid()));
        assertNotNull(s.objectTree().entry(e.uuid()));
    }

    @Test
    void iconRoutesToIconCategory() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        Vault v = s.vault();

        IconNode icon = s.iconTree().createIcon("图标", new byte[]{1, 2, 3}, "png");
        UUID iconCat = v.categoryUuid("icon");
        assertEquals(iconCat, parentUuidOf(icon));
        assertNotNull(v.groupDek(iconCat));
        assertNotNull(s.iconTree().find(icon.uuid()));
    }

    @Test
    void sshKeyRoutesToSshKeyCategory() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        Vault v = s.vault();

        SshKeyNode key = s.sshKeyTree().createSshKey("密钥", "-----BEGIN OPENSSH PRIVATE KEY-----");
        UUID sshCat = v.categoryUuid("sshKey");
        assertEquals(sshCat, parentUuidOf(key));
        assertNotNull(v.groupDek(sshCat));
        assertNotNull(s.sshKeyTree().find(key.uuid()));
    }

    @Test
    void remoteRoutesToRemoteCategory() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        Vault v = s.vault();

        SshKeyNode key = s.sshKeyTree().createSshKey("密钥", "-----BEGIN OPENSSH PRIVATE KEY-----");
        RemoteNode remote = s.remoteTree().addRemote("origin", "git@example.com:repo.git", Ref.nodeKey(key.uuid()));
        UUID remoteCat = v.categoryUuid("remote");
        assertEquals(remoteCat, parentUuidOf(remote));
        assertNotNull(v.groupDek(remoteCat));
        assertNotNull(s.remoteTree().find(remote.uuid()));
    }

    @Test
    void allFourCategoriesRegisteredAndReachableAfterRelock() {
        char[] pw = "pw".toCharArray();
        Sanctum s = Sanctum.createAndUnlock(dir, pw, 8192, 2, 1);
        EntryNode e = s.objectTree().createEntry(null, "条目",
                new EntryFields("s3cret", null, "alice", List.of()));
        IconNode icon = s.iconTree().createIcon("图标", new byte[]{7}, "png");

        UUID entryUuid = e.uuid();
        UUID iconUuid = icon.uuid();
        for (String disc : new String[]{"password", "icon", "sshKey", "remote"}) {
            assertNotNull(s.vault().categoryUuid(disc), "category 未注册: " + disc);
        }

        s.close();
        // 重新解锁：类别层需经 rootDek 解密、类别 DEK 需经类别节点 DEK 解密
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock(pw);
        for (String disc : new String[]{"password", "icon", "sshKey", "remote"}) {
            assertNotNull(s2.vault().categoryUuid(disc), "relock 后 category 丢失: " + disc);
            assertNotNull(s2.vault().groupDek(s2.vault().categoryUuid(disc)), "relock 后 category DEK 丢失: " + disc);
        }
        // 顶层条目与图标仍可读（类别 DEK 路由命中）
        assertEquals("s3cret", s2.objectTree().entry(entryUuid).password());
        assertEquals("alice", s2.objectTree().entry(entryUuid).username());
        assertNotNull(s2.iconTree().find(iconUuid));
        s2.close();
    }
}
