package com.flora.sanctum.app.io.importer.kdbx;

import com.flora.sanctum.app.io.importer.ImportException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证导入器在 KDBX4 文件包含「当前不读取」的高级语法（条目历史、附件/二进制、
 * 回收站/DeletedObjects、自定义数据 CustomData、自定义图标 CustomIcons、自定义属性、
 * 嵌套分组、Unicode）时，仍能正确读取主要内容（分组/条目/字符串字段/时间），且这些内容
 * 不会串入或破坏主数据。
 * <p>样本 {@code Kdbx4Features.kdbx} 由 pykeepass（独立实现）生成：KDBX 4.0、Argon2d、
 * 密码 "t"，包含上述全部高级语法。导入器无需理解这些语法，只需忽略它们并正确抽取主内容。</p>
 */
class Kdbx4SyntaxRobustnessTest {

    private byte[] loadResource(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(name)) {
            assertNotNull(in, "找不到测试向量资源: " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    @Test
    void parsesFileWithAdvancedSyntaxFeatures() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/app/io/importer/kdbx/Kdbx4Features.kdbx");
        KdbxDocument doc = KdbxParser.parse(data, "test".toCharArray(), null);

        assertNotNull(doc.root, "根分组为空");
        assertEquals("Root", doc.root.name, "根分组名应为 Root");

        // 嵌套分组应正确解析：Root > "Group 中文一层" > "SubGroup 第二层"
        KdbxDocument.KdbxGroup g1 = findGroup(doc.root, "Group 中文一层");
        assertNotNull(g1, "应解析出一级嵌套分组");
        KdbxDocument.KdbxGroup g2 = findGroup(g1, "SubGroup 第二层");
        assertNotNull(g2, "应解析出二级嵌套分组");

        // 活动条目（标题含 Unicode）应正确读取，且受保护密码字段正确解密。
        // 该条目经过两次历史修改，当前（活动）密码为最后一次的值，而非历史版本。
        KdbxDocument.KdbxEntry e = findEntryByTitle(doc.root, "标题 αβγ");
        assertNotNull(e, "应解析出 Unicode 标题条目");
        KdbxDocument.KdbxField pwd = e.fields.get("Password");
        assertNotNull(pwd, "应存在 Password 字段");
        assertEquals("changed-twice", pwd.value, "受保护密码应解密为当前活动值（非历史版本）");
        assertEquals("user①", e.fields.get("UserName").value, "UserName 应正确读取");

        // 自定义属性以 <String> 形式出现，应作为可读字段保留（不破坏主内容）
        KdbxDocument.KdbxField custom = e.fields.get("CustomKey");
        assertNotNull(custom, "自定义属性应作为字段保留");
        assertEquals("CustomVal-自定义", custom.value, "受保护自定义属性应正确解密");

        // 历史条目（<History>）内的受保护字段虽不保留，但必须参与内层密钥流推进，
        // 否则后续字段会解密错位。下面断言历史密码未串入活动条目。
        assertNotEquals("changed-once", pwd.value, "活动密码不应是历史版本");
        assertNotEquals("s3cr3t-保护", pwd.value, "活动密码不应是初始历史版本");

        // 被删除的条目/分组（DeletedObjects）不应作为活动条目/分组出现于任何位置，
        // 且解析主内容时不应崩溃。
        assertNull(findEntryByTitle(doc.root, "NestedEntry"),
                "已删除条目不应出现在任何分组下");
        assertNull(findGroup(doc.root, "EmptyGroup"),
                "已删除分组不应出现在任何位置");
    }

    @Test
    void wrongPasswordRejected() throws Exception {
        byte[] data = loadResource("/com/flora/sanctum/app/io/importer/kdbx/Kdbx4Features.kdbx");
        assertThrows(ImportException.class,
                () -> KdbxParser.parse(data, "wrong".toCharArray(), null),
                "错误密码应被拒绝");
    }

    private static KdbxDocument.KdbxGroup findGroup(KdbxDocument.KdbxGroup g, String name) {
        if (name.equals(g.name)) {
            return g;
        }
        for (KdbxDocument.KdbxGroup c : g.groups) {
            KdbxDocument.KdbxGroup f = findGroup(c, name);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    private static KdbxDocument.KdbxEntry findEntryByTitle(KdbxDocument.KdbxGroup g, String title) {
        List<KdbxDocument.KdbxEntry> all = new ArrayList<>();
        collectEntries(g, all);
        for (KdbxDocument.KdbxEntry e : all) {
            if (title.equals(e.name)) {
                return e;
            }
        }
        return null;
    }

    private static void collectEntries(KdbxDocument.KdbxGroup g, List<KdbxDocument.KdbxEntry> out) {
        out.addAll(g.entries);
        for (KdbxDocument.KdbxGroup c : g.groups) {
            collectEntries(c, out);
        }
    }
}
