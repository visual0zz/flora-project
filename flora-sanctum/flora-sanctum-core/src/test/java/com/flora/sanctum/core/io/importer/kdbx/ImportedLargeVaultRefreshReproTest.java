package com.flora.sanctum.core.io.importer.kdbx;

import com.flora.sanctum.core.model.ExternalKeyService;
import com.flora.sanctum.core.model.Ref;
import com.flora.sanctum.core.model.Sanctum;
import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.FieldNode;
import com.flora.sanctum.core.model.tree.GroupNode;
import com.flora.sanctum.core.model.tree.IconNode;
import com.flora.sanctum.core.model.tree.IconTree;
import com.flora.sanctum.core.model.tree.ObjectTree;
import com.flora.sanctum.core.model.tree.TreeNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

/**
 * 复现"导入大仓库(≈512~600 块)后重开解锁，主界面无响应/不报错"：
 * 在 core 层用程序生成贴近真实导入形态的大仓库（深层嵌套组 + 大量条目 + totp + 外部密钥 + 自定义图标），
 * 重开解锁后，忠实模拟 app 层 {@code rebuildGroupTree}/{@code refreshEntryList} 的全部模型访问序列。
 * <p>app 层 GUI 的真实刷新跑在 EDT 上，其异常会被 Swing 默认处理器静默吞掉（仅打印 stderr），
 * 本测试在 core 层把它们逐个 step 化，任一步抛异常即以带标签的 AssertionError 暴露真实根因。</p>
 */
class ImportedLargeVaultRefreshReproTest {

    /** 1x1 透明 PNG，用于自定义图标字节（ImageIO 可解）。 */
    private static final byte[] PNG_1PX = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMBAQDJ/pLvAAAAAElFTkSuQmCC");

    @Test
    void reopenLargeImportedVaultExercisesGuiRefresh(@TempDir Path dir) throws Exception {
        Path vault = dir.resolve("vault");
        String pw = "test";
        Sanctum sanctum = Sanctum.createAndUnlock(vault, pw.toCharArray(), 8192, 2, 1);

        // 1) 程序生成大仓库（贴近真实导入：深层嵌套 + 大量条目 + totp + 外部密钥 + 自定义图标）
        ObjectTree tree = sanctum.objectTree();
        IconTree iconTree = sanctum.iconTree();
        GroupNode root = tree.createGroup(null, "导入根");
        // 深层链：root→g0→g1→…→g59（测试递归深度）
        GroupNode chain = root;
        for (int i = 0; i < 60; i++) {
            chain = chain.createChildGroup("链-" + i);
        }
        // 在链末端 + 根下各放一批条目
        List<GroupNode> hosts = new ArrayList<>();
        hosts.add(root);
        hosts.add(chain);
        int entryCount = 0;
        for (GroupNode host : hosts) {
            for (int i = 0; i < 40; i++) {
                EntryNode e = host.createEntry("条目-" + entryCount,
                        new com.flora.sanctum.core.model.EntryFields("pw" + entryCount, "https://x.com", "user" + entryCount, List.of("tagA", "tagB")));
                e.writeField("note", "备注内容 " + entryCount, null);
                e.writeField("totp", "otpauth://totp/x?secret=AAA", "totp");
                // 自定义图标（经 iconTree 建节点并挂引用）
                IconNode ic = iconTree.createIcon("ico-" + entryCount, PNG_1PX, "png");
                e.setIcon(ic.uuid());
                // 外部密钥字段
                new ExternalKeyService(sanctum).createExternalKey(e.uuid(),
                        "extkey-" + entryCount, ("keymat-" + entryCount).getBytes(StandardCharsets.UTF_8), "desc");
                entryCount++;
            }
        }
        System.out.println("[repro] 生成条目数=" + entryCount + " 节点数≈" + sanctum.objectTree().nodes().size());

        // 2) 关闭并重新打开（解锁），复现用户重开场景
        sanctum.close();
        Sanctum s2 = Sanctum.open(vault);
        s2.unlock(pw.toCharArray());
        ObjectTree ot = s2.objectTree();

        // 3) 忠实模拟 app 层 rebuildGroupTree / refreshEntryList 的全部模型访问
        // 3a) rebuildGroupTree 等价递归（与旧 GUI 一样【无】环检测，仅验证大数据量/深层不抛异常）
        step("rebuildGroupTree 等价递归(无环检测)", () -> {
            for (GroupNode g : ot.rootGroups()) {
                if (g.deleted()) {
                    continue;
                }
                guiAddGroupNode(ot, null, g.uuid(), g.name());
            }
        });

        // 3b) groupsById 缓存构建（rebuildGroupTree 内会构建）
        Map<UUID, String[]> groupsById = new LinkedHashMap<>();
        for (TreeNode n : ot.nodes()) {
            if (n instanceof GroupNode g && !n.deleted()) {
                groupsById.put(g.uuid(), new String[]{parentHex(ot, g.uuid()), g.name()});
            }
        }

        // 3c) refreshEntryList 默认分支等价：沿根组/根条目 + 各组的子节点建列表
        step("refreshEntryList 默认分支等价", () -> {
            List<TreeNode> items = new ArrayList<>();
            items.addAll(ot.rootGroups());
            items.addAll(ot.rootEntries());
            for (TreeNode n : items) {
                if (n.deleted()) {
                    continue;
                }
                if (n instanceof GroupNode g) {
                    for (TreeNode c : g.children()) {
                        touch(c);
                    }
                } else if (n instanceof EntryNode e) {
                    touch(e);
                }
            }
        });

        // 3d) folderPathOf 等价：沿父链建路径（遇 null/非组即终止，不应死循环）
        step("folderPathOf 等价(沿父链)", () -> {
            for (TreeNode n : ot.nodes()) {
                if (n instanceof EntryNode e) {
                    walkPath(groupsById, e.parentRef());
                }
            }
        });

        // 3e) matchesFilter 等价：扫描条目名 + 字段名/值
        step("matchesFilter 等价", () -> {
            for (TreeNode n : ot.nodes()) {
                if (n instanceof EntryNode e && !e.deleted()) {
                    touch(e);
                }
            }
        });

        // 3f) trash()
        step("sanctum.trash()", () -> s2.trash());

        // 3g) ExternalKeyService.list()
        step("ExternalKeyService.list()", () -> new ExternalKeyService(s2).list());

        // 3h) totpFields 等价 + totpItemName + safeTotp
        step("totpFields 等价(totpItemName/safeTotp)", () -> {
            for (TreeNode n : ot.nodes()) {
                if (n instanceof EntryNode e && !e.deleted()) {
                    for (FieldNode f : e.fields()) {
                        if ("totp".equals(f.kind())) {
                            String pid = f.data() == null ? null : f.data().getString("parent");
                            if (pid != null) {
                                EntryNode owner = ot.entry(com.flora.sanctum.core.util.UuidHex.fromHex(pid));
                                owner.name();
                            }
                            f.fieldName();
                            f.value();
                        }
                    }
                }
            }
        });

        // 3i) iconTree + ImageIO 解码（iconById 自身异常安全，这里验证字节可解）
        step("iconTree().icons() + ImageIO 解码", () -> {
            for (IconNode ic : s2.iconTree().icons()) {
                byte[] data = ic.iconData();
                if (data != null && data.length > 0) {
                    try {
                        ImageIO.read(new java.io.ByteArrayInputStream(data));
                    } catch (java.io.IOException ignore) {
                    }
                }
            }
        });

        System.out.println("[repro] 全部模型访问通过，未复现异常（大数据量/深层嵌套安全）");
    }

    /** 模拟 app 的 addGroupNode（无环检测版，仅用于验证大数据量下不抛异常）。 */
    private void guiAddGroupNode(ObjectTree ot, Object ignore, UUID id, String name) {
        GroupNode g = ot.group(id);
        if (g != null) {
            for (GroupNode child : g.childGroups()) {
                if (child.deleted()) {
                    continue;
                }
                guiAddGroupNode(ot, null, child.uuid(), child.name());
            }
        }
    }

    private String parentHex(ObjectTree ot, UUID uuid) {
        TreeNode n = ot.find(uuid);
        return n == null ? null : n.parentRef();
    }

    private void walkPath(Map<UUID, String[]> groupsById, String parent) {
        String cur = parent;
        int guard = 0;
        while (cur != null && !cur.isBlank() && guard++ < 10000) {
            UUID id;
            try {
                id = UUID.fromString(cur);
            } catch (IllegalArgumentException ex) {
                break;
            }
            String[] info = groupsById.get(id);
            if (info == null) {
                break;
            }
            cur = info[0];
        }
    }

    private void touch(TreeNode n) {
        if (n instanceof EntryNode e) {
            e.name();
            e.password();
            e.username();
            e.url();
            e.notes();
            e.labels();
            e.iconRef();
            for (FieldNode f : e.fields()) {
                f.fieldName();
                f.value();
                f.kind();
            }
        } else if (n instanceof GroupNode g) {
            g.name();
            g.iconRef();
        }
    }

    private void step(String name, Runnable r) {
        try {
            r.run();
            System.out.println("[repro] OK  : " + name);
        } catch (Throwable t) {
            throw new AssertionError("复现步骤失败: " + name, t);
        }
    }
}
