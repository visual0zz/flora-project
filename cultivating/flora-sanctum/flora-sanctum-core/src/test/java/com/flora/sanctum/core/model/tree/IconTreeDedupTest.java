package com.flora.sanctum.core.model.tree;

import com.flora.sanctum.core.model.Sanctum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图标库的内容去重（{@link IconTree#findOrCreate}）：
 * 字节相同的图标只存一份，且不影响 {@link IconTree#createIcon}「总是新建」的原有语义。
 */
class IconTreeDedupTest {

    /** 最小合法 1×1 透明 PNG。 */
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

    @Test
    void findOrCreateReusesIdenticalBytes(@TempDir Path dir) throws Exception {
        Sanctum s = Sanctum.createAndUnlock(dir.resolve("vault"), "pw".toCharArray(), 8192, 2, 1);
        IconTree icons = s.iconTree();

        IconNode a = icons.findOrCreate("first.png", PNG_1X1, "png");
        IconNode b = icons.findOrCreate("second.png", PNG_1X1, "png");

        assertEquals(a.uuid(), b.uuid(), "字节相同的图标应复用同一节点，不产生副本");
        assertEquals(1, icons.icons().size(), "库内应只有一份图标");
        assertEquals("first.png", b.name(), "复用时应保留已有图标的名称");
    }

    @Test
    void createIconStillAlwaysCreates(@TempDir Path dir) throws Exception {
        Sanctum s = Sanctum.createAndUnlock(dir.resolve("vault2"), "pw".toCharArray(), 8192, 2, 1);
        IconTree icons = s.iconTree();

        IconNode a = icons.createIcon("a.png", PNG_1X1, "png");
        IconNode b = icons.createIcon("b.png", PNG_1X1, "png");

        assertNotEquals(a.uuid(), b.uuid(), "createIcon 应保持『总是新建』的语义（不隐式去重）");
        assertEquals(2, icons.icons().size());
    }

    @Test
    void differentBytesAreNotMerged(@TempDir Path dir) throws Exception {
        Sanctum s = Sanctum.createAndUnlock(dir.resolve("vault3"), "pw".toCharArray(), 8192, 2, 1);
        IconTree icons = s.iconTree();

        IconNode a = icons.findOrCreate("a.png", PNG_1X1, "png");
        IconNode b = icons.findOrCreate("b.png", new byte[]{1, 2, 3, 4}, "png");

        assertNotEquals(a.uuid(), b.uuid(), "字节不同的图标不应被合并");
        assertEquals(2, icons.icons().size());
    }
}
