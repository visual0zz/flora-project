package com.flora.sanctum.core.io.importer.kdbx;

import com.flora.sanctum.core.io.importer.ImportContext;
import com.flora.sanctum.core.io.importer.ImportListeners;
import com.flora.sanctum.core.io.importer.Importer;
import com.flora.sanctum.core.io.importer.kdbx.KdbxImporter;
import com.flora.sanctum.core.model.ExternalKeyService;
import com.flora.sanctum.core.model.Ref;
import com.flora.sanctum.core.model.Sanctum;
import com.flora.sanctum.core.model.TrashView;
import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.FieldNode;
import com.flora.sanctum.core.model.tree.GroupNode;
import com.flora.sanctum.core.model.tree.IconNode;
import com.flora.sanctum.core.model.tree.IconTree;
import com.flora.sanctum.core.model.tree.TreeNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

/**
 * 复现"导入 KDBX 后重开仓库，解锁后界面无反应"：模拟 app 的解锁后刷新路径
 * （rebuildGroupTree / refreshEntryList / iconById）对导入数据的全部模型访问。
 */
class KdbxImportReopenReproTest {

    private byte[] readResource(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/com/flora/sanctum/core/io/importer/kdbx/Kdbx4Features.kdbx")) {
            if (in == null) {
                throw new IllegalStateException("找不到测试资源: " + name);
            }
            return in.readAllBytes();
        }
    }

    @Test
    void reopenAfterKdbxImportExercisesGuiModelAccess(@TempDir Path dir) throws Exception {
        Path vault = dir.resolve("vault");
        String pw = "test";
        // 1) 建库
        Sanctum sanctum = Sanctum.createAndUnlock(vault, pw.toCharArray(), 8192, 2, 1);

        // 2) 用真实 KDBX 文件经 KdbxImporter 导入（与 app 路径一致）
        byte[] kdbx = readResource("Kdbx4Features.kdbx");
        Path kdbxFile = dir.resolve("in.kdbx");
        Files.write(kdbxFile, kdbx);
        ImportContext ctx = ImportContext.builder(sanctum.objectTree())
                .iconTree(sanctum.iconTree())
                .password(pw.toCharArray())
                .listener(ImportListeners.console())
                .build();
        Importer imp = new KdbxImporter();
        var result = imp.importFile(kdbxFile, ctx);
        System.out.println("[repro] 导入结果: " + result);

        // 3) 关闭并重新打开（解锁），复现用户重开场景
        sanctum.close();
        Sanctum s2 = Sanctum.open(vault);
        s2.unlock(pw.toCharArray());

        // 4) 模拟 rebuildGroupTree / refreshEntryList 的全部模型访问
        step("objectTree().rootGroups()", () -> {
            for (GroupNode g : s2.objectTree().rootGroups()) {
                traverse(g, new HashSet<>());
            }
        });
        step("objectTree().rootEntries()", () -> {
            for (EntryNode e : s2.objectTree().rootEntries()) {
                touchEntry(e);
            }
        });
        step("objectTree().nodes()", () -> {
            for (TreeNode n : s2.objectTree().nodes()) {
                if (n instanceof EntryNode e) {
                    touchEntry(e);
                }
            }
        });
        step("sanctum.trash()", () -> {
            TrashView tv = s2.trash();
            for (TrashView.TrashKind k : TrashView.TrashKind.values()) {
                for (UUID id : trashIds(tv, k)) {
                    s2.findNode(id); // 仅访问，不要求存在
                }
            }
        });
        step("ExternalKeyService.list()", () -> new ExternalKeyService(s2).list());
        step("iconTree().icons() + ImageIO 解码", () -> {
            IconTree it = s2.iconTree();
            for (IconNode ic : it.icons()) {
                byte[] data = ic.iconData();
                if (data != null && data.length > 0) {
                    // 复现 iconById 的 ImageIO 解码（iconById 自身异常安全，这里仅验证字节可解）
                    try {
                        ImageIO.read(new java.io.ByteArrayInputStream(data));
                    } catch (java.io.IOException ignore) {
                    }
                }
            }
        });
        step("totpFields 复制（nodes + fields kind）", () -> {
            for (TreeNode n : s2.objectTree().nodes()) {
                if (n instanceof EntryNode e) {
                    for (FieldNode f : e.fields()) {
                        if ("totp".equals(f.kind())) {
                            f.value();
                        }
                    }
                }
            }
        });
        System.out.println("[repro] 全部模型访问通过，未复现异常");
    }

    private void traverse(GroupNode g, Set<UUID> visited) {
        if (g == null || !visited.add(g.uuid())) {
            return; // 环检测：已访问则停止，避免无限递归
        }
        for (GroupNode c : g.childGroups()) {
            traverse(c, visited);
        }
        for (EntryNode e : g.entries()) {
            touchEntry(e);
        }
    }

    private void touchEntry(EntryNode e) {
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
    }

    @SuppressWarnings("unchecked")
    private List<UUID> trashIds(TrashView tv, TrashView.TrashKind k) {
        try {
            java.lang.reflect.Method m = TrashView.class.getDeclaredMethod(k.name().toLowerCase() + "Ids");
            m.setAccessible(true);
            Object r = m.invoke(tv);
            return r instanceof List<?> list ? (List<UUID>) list : List.of();
        } catch (Exception ex) {
            // 方法名不匹配时退化为空，避免掩蓋真正的复现异常
            return List.of();
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
