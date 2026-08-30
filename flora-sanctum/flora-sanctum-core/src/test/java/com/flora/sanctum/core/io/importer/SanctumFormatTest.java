package com.flora.sanctum.core.io.importer;

import com.flora.sanctum.core.io.exporter.ExportException;
import com.flora.sanctum.core.io.exporter.Exporter;
import com.flora.sanctum.core.io.exporter.Exporters;
import com.flora.sanctum.core.model.EntryFields;
import com.flora.sanctum.core.model.Sanctum;
import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.FieldNode;
import com.flora.sanctum.core.model.tree.GroupNode;
import com.flora.sanctum.core.model.tree.ObjectTree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanctum 自有格式（CSV / JSON）导出再导入的往返一致性测试：
 * 验证组层级、条目预设字段、标签与自定义字段在往返后保持结构一致。
 */
@SuppressWarnings("osmetes:secret") // 测试假密码
class SanctumFormatTest {

    @TempDir
    Path dir;

    @Test
    void jsonRoundTripPreservesStructure() throws Exception {
        roundTrip("Sanctum JSON", "export.json");
    }

    @Test
    void csvRoundTripPreservesStructure() throws Exception {
        roundTrip("Sanctum CSV", "export.csv");
    }

    private void roundTrip(String format, String fileName) throws Exception {
        // 源仓库：构建含嵌套组、预设字段、标签、自定义字段的数据
        Sanctum src = Sanctum.createAndUnlock(dir.resolve("src"), "pw".toCharArray(), 8192, 2, 1);
        ObjectTree st = src.objectTree();
        GroupNode social = st.createGroup(null, "社交");
        GroupNode weibo = social.createChildGroup("微博分组");
        weibo.createEntry("微博账号", new EntryFields("s3cret", "https://weibo.com", "alice",
                List.of("work", "important")));
        EntryNode top = st.createEntry(null, "顶层条目",
                new EntryFields("toppw", null, "bob", List.of("misc")));
        FieldNode note = top.createField("memo", "自定义备注", "text");
        assertNotNull(note);

        // 导出到临时文件
        Path file = dir.resolve(fileName);
        Exporter exporter = Exporters.all().stream()
                .filter(e -> e.formatName().equals(format)).findFirst().orElseThrow();
        exporter.exportTo(file, st);

        // 目标仓库：导入
        Sanctum dst = Sanctum.createAndUnlock(dir.resolve("dst"), "pw".toCharArray(), 8192, 2, 1);
        ObjectTree dt = dst.objectTree();
        Importer importer = Importer.forFormatName(format).orElseThrow();
        ImportResult result = importer.importFile(file,
                ImportContext.builder(dt).build());
        assertTrue(result.entries >= 2, "应导入至少 2 个条目，实际 " + result.entries);
        assertTrue(result.groups >= 2, "应导入至少 2 个组，实际 " + result.groups);

        // 断言结构：源有 1 个顶层组（含 1 个子组）+ 1 个顶层条目
        assertEquals(1, dt.rootGroups().size(), "顶层组数量");
        assertEquals(1, dt.rootEntries().size(), "顶层条目数量");
        GroupNode socialBack = dt.rootGroups().stream()
                .filter(g -> "社交".equals(g.name())).findFirst().orElseThrow();
        assertEquals(1, socialBack.childGroups().size());
        assertEquals("微博分组", socialBack.childGroups().get(0).name());
        EntryNode weiboBack = socialBack.childGroups().get(0).entries().stream()
                .filter(e -> "微博账号".equals(e.name())).findFirst().orElseThrow();
        assertEquals("s3cret", weiboBack.password());
        assertEquals("https://weibo.com", weiboBack.url());
        assertEquals("alice", weiboBack.username());
        assertEquals(List.of("work", "important"), weiboBack.labels());

        EntryNode topBack = dt.rootEntries().stream()
                .filter(e -> "顶层条目".equals(e.name())).findFirst().orElseThrow();
        assertEquals("toppw", topBack.password());
        assertEquals("bob", topBack.username());
        assertEquals(List.of("misc"), topBack.labels());
        assertEquals(1, topBack.fields().size());
        assertEquals("memo", topBack.fields().get(0).fieldName());
        assertEquals("自定义备注", topBack.fields().get(0).value());
    }

    @Test
    void csvRejectsUnknownExtension() {
        Exporter exporter = Exporters.all().stream()
                .filter(e -> e.formatName().equals("Sanctum CSV")).findFirst().orElseThrow();
        assertTrue(exporter.supports(Path.of("a.csv")));
        assertTrue(!exporter.supports(Path.of("a.json")));
        // 非法调用（不存在文件）应抛 ExportException 而非其它
        try {
            exporter.exportTo(dir.resolve("missing").resolve("x.csv"),
                    Sanctum.createAndUnlock(dir.resolve("e"), "pw".toCharArray(), 8192, 2, 1).objectTree());
        } catch (ExportException expected) {
            // 目录不存在导致写入失败，属预期
        } catch (Exception ex) {
            throw new AssertionError("应抛 ExportException", ex);
        }
    }
}
