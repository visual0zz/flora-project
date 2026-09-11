package com.flora.sanctum.core.model;
import com.flora.sanctum.core.crypto.KeyIdDeriver;
import com.flora.sanctum.core.model.tree.*;
import com.flora.sanctum.core.model.vault.Vault;
import com.flora.sanctum.core.store.Block;
import com.flora.sanctum.core.util.UuidHex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
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

    /**
     * keyId↔parent 一致性回归：sshKey / remote 节点的改写（rename / update）必须把块加密在各自
     * category 的活跃 DEK 之下（parent 指向该 category），而非沿用旧模式的 rootDek。
     * 直接校验块头的 keyId 经 repoKeyIdSeed 反解的 dekId 等于 category 活跃 DEK 的 dekId，
     * 且不等于 rootDek 的 dekId——这能区分"用 category DEK 加密"与"用 rootDek 加密"两种实现。
     */
    @Test
    void renamedSshKeyAndRemoteUseCategoryDek() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        Vault v = s.vault();

        SshKeyNode key = s.sshKeyTree().createSshKey("k", "-----BEGIN OPENSSH PRIVATE KEY-----");
        key.rename("renamed");
        key.update("-----BEGIN OPENSSH PRIVATE KEY----- updated", "ssh-rsa AAAA");
        RemoteNode remote = s.remoteTree().addRemote("origin", "git@example.com:r.git", Ref.nodeKey(key.uuid()));
        remote.rename("renamedRemote");
        remote.update("git@example.com:r2.git", null);

        byte[] sshCatDekId = KeyIdDeriver.dekId(v.groupDek(v.categoryUuid("sshKey")));
        byte[] remoteCatDekId = KeyIdDeriver.dekId(v.groupDek(v.categoryUuid("remote")));
        byte[] rootDekId = KeyIdDeriver.dekId(v.rootDek());

        assertArrayEquals(sshCatDekId, blockDekId(s, key.uuid()),
                "sshKey 节点块应加密于 sshKey category 活跃 DEK");
        assertFalse(Arrays.equals(rootDekId, blockDekId(s, key.uuid())),
                "sshKey 节点块不应加密于 rootDek");
        assertArrayEquals(remoteCatDekId, blockDekId(s, remote.uuid()),
                "remote 节点块应加密于 remote category 活跃 DEK");
        assertFalse(Arrays.equals(rootDekId, blockDekId(s, remote.uuid())),
                "remote 节点块不应加密于 rootDek");

        // 改写后仍可解密读回（relock 往返）
        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock("pw".toCharArray());
        assertEquals("renamed", s2.sshKeyTree().find(key.uuid()).name());
        assertEquals("renamedRemote", s2.remoteTree().find(remote.uuid()).name());
        s2.close();
    }

    /** 反解某块头 (nonce, keyId) 得到其内部 dekId（用于校验块加密所用 DEK）。 */
    private static byte[] blockDekId(Sanctum s, UUID nodeUuid) {
        Block b = s.store().scan().stream()
                .filter(x -> nodeUuid.equals(x.uuid())).findFirst()
                .orElseThrow(() -> new AssertionError("块未找到: " + nodeUuid));
        byte[] env = b.unmasked();
        // 信封头：magic(6)+version(1)+flags(1)+nonce(12)+keyId(4)
        byte[] nonce = Arrays.copyOfRange(env, 8, 20);
        byte[] keyId = Arrays.copyOfRange(env, 20, 24);
        return KeyIdDeriver.resolveDekId(s.vault().repoKeyIdSeed(), nonce, keyId);
    }
}
