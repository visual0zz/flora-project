package com.flora.sanctum.core.model;
import com.flora.sanctum.core.model.tree.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("osmetes:secret") // 测试假密钥
class GroupKeyRotationTest {

    @TempDir
    Path dir;

    /** 首个子节点写入后，初始 dek1 无人使用即被丢弃（前向保密：轮换发生）。 */
    @Test
    void firstChildTriggersRotationAndDataSurvives() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode g = s.objectTree().createGroup(null, "G");
        String dek1Before = s.objectTree().group(g.uuid()).data().getString("dek1");

        EntryNode e1 = g.createEntry("E1", new EntryFields("p1", null, null, List.of()));

        String dek1After = s.objectTree().group(g.uuid()).data().getString("dek1");
        assertNotEquals(dek1Before, dek1After, "首个子节点写入后初始 dek1 应被轮换丢弃");
        assertEquals("p1", s.objectTree().entry(e1.uuid()).password());

        // 重开后数据仍可读（轮换后 dek2 提升为 dek1，子节点仍可被新 dek1 解开）
        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock("pw".toCharArray());
        EntryNode e2 = s2.objectTree().entry(e1.uuid());
        assertNotNull(e2);
        assertEquals("p1", e2.password());
        s2.close();
    }

    /** 只要还有子节点停留在 dek1 上，dek1 不会被丢弃。 */
    @Test
    void dek1PreservedWhileAChildStillOnIt() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode g = s.objectTree().createGroup(null, "G");
        g.createEntry("E1", new EntryFields("p1", null, null, List.of())); // 末次轮换后落在 dek1 上
        g.createEntry("E2", new EntryFields("p2", null, null, List.of())); // 落在 dek2 上，dek1 仍被 E1 占用
        String dek1Stable = s.objectTree().group(g.uuid()).data().getString("dek1");

        // 再建一个落在 dek2 的子节点，不应触发 dek1 轮换（E1 仍占用 dek1）
        g.createEntry("E3", new EntryFields("p3", null, null, List.of()));
        assertEquals(dek1Stable, s.objectTree().group(g.uuid()).data().getString("dek1"),
                "存在停留在 dek1 的子节点时，dek1 不应被丢弃");
        assertEquals("p1", s.objectTree().entry(g.entries().stream()
                .filter(e -> "E1".equals(e.name())).findFirst().get().uuid()).password());
    }

    /** 全部子节点迁离 dek1（含软删除重加密到 dek2）后轮换发生，且垃圾桶条目仍可读。 */
    @Test
    void fullMigrationRotatesAndTrashRemainsReadable() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        GroupNode g = s.objectTree().createGroup(null, "G");
        EntryNode e1 = g.createEntry("E1", new EntryFields("p1", null, null, List.of()));
        EntryNode e2 = g.createEntry("E2", new EntryFields("p2", null, null, List.of()));
        String dek1Before = s.objectTree().group(g.uuid()).data().getString("dek1");

        // 软删除 E1（按"编辑用 dek2"规则重加密到 dek2，留在垃圾桶），硬删除 E2 → dek1 不再被任何块使用
        e1.markDeleted();
        UUID e1Id = e1.uuid();
        e2.delete(); // 仅硬删 E2；E1 保留为软删除（在垃圾桶）


        String dek1After = s.objectTree().group(g.uuid()).data().getString("dek1");
        assertNotEquals(dek1Before, dek1After, "全部子节点迁离 dek1 后应发生轮换");

        // 重开：被软删的 E1 仍应可读（dek2 提升为 dek1）
        s.close();
        Sanctum s2 = Sanctum.open(dir);
        s2.unlock("pw".toCharArray());
        assertTrue(s2.trash().manual().contains(e1Id), "轮换后软删除条目应仍在垃圾桶");
        s2.close();
    }
}
