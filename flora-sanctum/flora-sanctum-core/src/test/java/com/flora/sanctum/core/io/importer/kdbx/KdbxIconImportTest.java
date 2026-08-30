package com.flora.sanctum.core.io.importer.kdbx;

import com.flora.sanctum.core.io.importer.ImportContext;
import com.flora.sanctum.core.io.importer.ImportResult;
import com.flora.sanctum.core.icon.BuiltinIcons;
import com.flora.sanctum.core.model.Ref;
import com.flora.sanctum.core.model.Sanctum;
import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.GroupNode;
import com.flora.sanctum.core.model.tree.IconTree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 KDBX 导入的图标映射：
 * <ul>
 *   <li>KeePass 自定义图标（CustomIconUUID）按 UUID 去重复制进 iconTree，并建 node 引用；</li>
 *   <li>KeePass 内置图标（IconID）在本轮内按 iconId 稳定地随机映射到某个 Sanctum 内置图标（builtin 引用）；</li>
 *   <li>无图标引用时节点 iconRef 为 null。</li>
 * </ul>
 */
class KdbxIconImportTest {

    /** 最小合法 1×1 透明 PNG（仅用于验证字节被原样复制）。 */
    private static final byte[] PNG_1X1 = {
            (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0D, (byte) 0x49, (byte) 0x48, (byte) 0x44, (byte) 0x52,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
            (byte) 0x08, (byte) 0x06, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x1F, (byte) 0x15, (byte) 0xC4, (byte) 0x89,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0A, (byte) 0x49, (byte) 0x44, (byte) 0x41, (byte) 0x54,
            (byte) 0x78, (byte) 0x9C, (byte) 0x63, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x05, (byte) 0x00, (byte) 0x01, (byte) 0x0D, (byte) 0x0A, (byte) 0x2D, (byte) 0xB4,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x49, (byte) 0x45, (byte) 0x4E, (byte) 0x44,
            (byte) 0xAE, (byte) 0x42, (byte) 0x60, (byte) 0x82
    };

    private static final String CUSTOM_UUID = "0123456789abcdef0123456789abcdef";

    @Test
    void importsCustomAndBuiltinIcons(@TempDir Path dir) throws Exception {
        Sanctum sanctum = Sanctum.createAndUnlock(dir.resolve("vault"), "pw".toCharArray(), 8192, 2, 1);
        var tree = sanctum.objectTree();
        IconTree iconTree = sanctum.iconTree();

        KdbxDocument.KdbxGroup root = new KdbxDocument.KdbxGroup();
        root.name = "Root";

        // 子分组使用自定义图标（与 e1 共享同一 customIconUuid，应去重为同一 node）
        KdbxDocument.KdbxGroup sub = new KdbxDocument.KdbxGroup();
        sub.name = "Sub";
        sub.customIconUuid = CUSTOM_UUID;
        root.groups.add(sub);

        KdbxDocument.KdbxEntry e1 = new KdbxDocument.KdbxEntry();
        e1.name = "CustomIconEntry";
        e1.customIconUuid = CUSTOM_UUID;
        e1.fields.put("UserName", new KdbxDocument.KdbxField("alice", false));
        e1.fields.put("Password", new KdbxDocument.KdbxField("secret", true));
        sub.entries.add(e1);

        // 内置图标（iconId=3），应稳定映射到一个内置图标
        KdbxDocument.KdbxEntry e2 = new KdbxDocument.KdbxEntry();
        e2.name = "BuiltinIconEntry";
        e2.iconId = 3;
        e2.fields.put("UserName", new KdbxDocument.KdbxField("bob", false));
        e2.fields.put("Password", new KdbxDocument.KdbxField("x", true));
        root.entries.add(e2);

        // 另一个内置图标（同为 iconId=3），应与 e2 映射一致（本轮稳定）
        KdbxDocument.KdbxEntry e3 = new KdbxDocument.KdbxEntry();
        e3.name = "BuiltinIconEntryAgain";
        e3.iconId = 3;
        e3.fields.put("UserName", new KdbxDocument.KdbxField("carol", false));
        e3.fields.put("Password", new KdbxDocument.KdbxField("y", true));
        root.entries.add(e3);

        // 无图标引用
        KdbxDocument.KdbxEntry e0 = new KdbxDocument.KdbxEntry();
        e0.name = "NoIconEntry";
        e0.fields.put("UserName", new KdbxDocument.KdbxField("dave", false));
        e0.fields.put("Password", new KdbxDocument.KdbxField("z", true));
        root.entries.add(e0);

        KdbxDocument doc = new KdbxDocument(root, Map.of(CUSTOM_UUID, PNG_1X1));

        ImportContext ctx = ImportContext.builder(tree).iconTree(iconTree).build();
        ImportResult result = KdbxMapper.map(doc, ctx);
        assertEquals(4, result.entries, "应导入 4 个条目");

        // 定位映射后的节点
        GroupNode top = tree.rootGroups().stream()
                .filter(g -> "Root".equals(g.name())).findFirst().orElseThrow();
        GroupNode subNode = top.childGroups().stream()
                .filter(g -> "Sub".equals(g.name())).findFirst().orElseThrow();
        EntryNode e1Node = subNode.entries().stream()
                .filter(e -> "CustomIconEntry".equals(e.name())).findFirst().orElseThrow();
        EntryNode e2Node = top.entries().stream()
                .filter(e -> "BuiltinIconEntry".equals(e.name())).findFirst().orElseThrow();
        EntryNode e3Node = top.entries().stream()
                .filter(e -> "BuiltinIconEntryAgain".equals(e.name())).findFirst().orElseThrow();
        EntryNode e0Node = top.entries().stream()
                .filter(e -> "NoIconEntry".equals(e.name())).findFirst().orElseThrow();

        // 自定义图标：node 引用 + 字节被原样复制 + 去重
        Ref subRef = subNode.iconRef();
        Ref e1Ref = e1Node.iconRef();
        assertNotNull(subRef, "子分组应带自定义图标引用");
        assertNotNull(e1Ref, "e1 应带自定义图标引用");
        assertEquals("node", subRef.scheme(), "自定义图标应为 node 引用");
        assertEquals(subRef, e1Ref, "相同 customIconUuid 应去重为同一 node 引用");

        byte[] stored = iconTree.find(subRef.nodeUuid()).iconData();
        assertArrayEquals(PNG_1X1, stored, "自定义图标字节应原样复制");

        // 内置图标：builtin 引用 + 落到某个内置图标名 + 本轮稳定
        Ref e2Ref = e2Node.iconRef();
        Ref e3Ref = e3Node.iconRef();
        assertNotNull(e2Ref, "e2 应带内置图标引用");
        assertEquals("builtin", e2Ref.scheme(), "内置图标应为 builtin 引用");
        List<String> libs = BuiltinIcons.names();
        assertFalse(libs.isEmpty(), "应存在内置图标库");
        assertTrue(libs.contains(e2Ref.id()), "内置图标引用应指向图标库中的某个名称");
        assertEquals(e2Ref, e3Ref, "相同 iconId 在本轮应映射到同一内置图标");

        // 无图标引用
        assertNull(e0Node.iconRef(), "无图标引用的条目 iconRef 应为 null");
    }

    @Test
    void notesFieldBecomesBuiltinPreset() throws Exception {
        Sanctum sanctum = Sanctum.createAndUnlock(
                Path.of(System.getProperty("java.io.tmpdir"), "kdbx-notes-" + System.nanoTime()),
                "pw".toCharArray(), 8192, 2, 1);
        var tree = sanctum.objectTree();

        KdbxDocument.KdbxGroup root = new KdbxDocument.KdbxGroup();
        root.name = "Root";

        KdbxDocument.KdbxEntry e = new KdbxDocument.KdbxEntry();
        e.name = "WithNotes";
        e.fields.put("UserName", new KdbxDocument.KdbxField("alice", false));
        e.fields.put("Password", new KdbxDocument.KdbxField("secret", true));
        e.fields.put("Notes", new KdbxDocument.KdbxField("这是备注\n第二行", false));
        e.fields.put("CustomKey", new KdbxDocument.KdbxField("cv", false));
        root.entries.add(e);

        ImportContext ctx = ImportContext.builder(tree).build();
        KdbxMapper.map(new KdbxDocument(root, Map.of()), ctx);

        EntryNode node = tree.rootGroups().stream()
                .filter(g -> "Root".equals(g.name())).findFirst().orElseThrow()
                .entries().stream()
                .filter(en -> "WithNotes".equals(en.name())).findFirst().orElseThrow();

        // Notes 转译为内置备注字段，而非附加自定义字段
        assertEquals("这是备注\n第二行", node.notes());
        assertTrue(node.fields().stream().noneMatch(f -> "notes".equals(f.fieldName())),
                "Notes 不应再作为附加自定义字段出现");
        // 其它自定义字段仍正常保留
        assertTrue(node.fields().stream().anyMatch(f -> "CustomKey".equals(f.fieldName())),
                "其它自定义字段应保留");
    }

    /**
     * 回归：KeePass 2.x 的 {@code <CustomIcon>} 把 UUID 放在属性上（base64）。早期实现误读子元素，
     * 导致所有自定义图标被跳过、图标库为空。此处端到端解析一份含 CustomIcons 的 KDBX 内层 XML，
     * 验证 {@link KdbxXml#parse} 能提取出图标字节，且条目经 {@code <CustomIconUUID>} 正确引用。
     */
    @Test
    void parseCustomIconsReadsUuidAttribute() throws Exception {
        byte[] uuid16 = hexToBytes(CUSTOM_UUID);
        String uuidB64 = Base64.getEncoder().encodeToString(uuid16);
        String pngB64 = Base64.getEncoder().encodeToString(PNG_1X1);
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<KeePassFile><Meta><CustomIcons>"
                + "<CustomIcon UUID=\"" + uuidB64 + "\"><Data>" + pngB64 + "</Data></CustomIcon>"
                + "</CustomIcons></Meta>"
                + "<Root><Group><UUID>" + Base64.getEncoder().encodeToString(new byte[16]) + "</UUID>"
                + "<Name>Root</Name>"
                + "<Entry><UUID>" + Base64.getEncoder().encodeToString(new byte[16]) + "</UUID>"
                + "<CustomIconUUID>" + uuidB64 + "</CustomIconUUID>"
                + "<String><Key>Title</Key><Value>t</Value></String>"
                + "<String><Key>Password</Key><Value>p</Value></String>"
                + "</Entry></Group></Root></KeePassFile>";
        // 内层头：InnerRandomStreamID=2（Salsa20）+ End 字段；其后为内层 XML（无受保护字段，无需实际解密）
        byte[] innerHeader = {
                0x01, 0x04, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, // id=1, len=4, value=2
                0x00, 0x00, 0x00, 0x00, 0x00                           // id=0 (End), len=0
        };
        byte[] inner = concat(innerHeader, xml.getBytes(StandardCharsets.UTF_8));

        KdbxDocument doc = KdbxXml.parse(inner);

        assertFalse(doc.customIcons.isEmpty(), "自定义图标应被解析（UUID 作为属性）");
        assertTrue(doc.customIcons.containsKey(CUSTOM_UUID), "自定义图标应以 hex uuid 为 key");
        assertArrayEquals(PNG_1X1, doc.customIcons.get(CUSTOM_UUID), "自定义图标字节应原样提取");
        assertEquals(CUSTOM_UUID, doc.root.entries.get(0).customIconUuid,
                "条目应经 <CustomIconUUID> 引用该自定义图标");
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
