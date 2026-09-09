package com.flora.sanctum.core.icon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内置图标库按 KeePass IconID 的映射（{@link BuiltinIcons#nameForIconId}）：
 * 编号落在库范围内时精确命中同名前缀的图标，超出范围时回退为对库大小取模。
 */
class BuiltinIconsIconIdMappingTest {

    /** KeePass 2.x 预制图标表的编号上界（含）。 */
    private static final int MAX_KEEPASS_ICON_ID = 68;

    private static final List<String> LIB = BuiltinIcons.names();

    @Test
    void iconIdInRangeMapsExactly() {
        assertEquals("00-key", BuiltinIcons.nameForIconId(0), "IconID 0 应映射到 00-key（钥匙）");
        assertEquals("01-earth", BuiltinIcons.nameForIconId(1), "IconID 1 应映射到 01-earth（地球）");
        assertEquals("48-folder", BuiltinIcons.nameForIconId(48), "IconID 48 应映射到 48-folder（文件夹）");
    }

    /**
     * 0–68 每个编号都必须精确命中自己那份图标，而不是被取模打散成别的编号——
     * 这正是「按 IconID 映射」相对旧的「一律取模」的语义价值所在。
     */
    @Test
    void everyKeePassIconIdMapsToItsOwnIcon() {
        for (int id = 0; id <= MAX_KEEPASS_ICON_ID; id++) {
            String name = BuiltinIcons.nameForIconId(id);
            String prefix = String.format("%02d-", id);
            assertNotNull(name, "编号 " + id + " 应能映射到某个图标（库是否删了该文件？）");
            assertTrue(name.startsWith(prefix),
                    "编号 " + id + " 应映射到前缀 " + prefix + " 的图标，实际为 " + name);
        }
    }

    /** 超出库范围的编号回退取模：编号等于库大小时应回绕到 0 号图标。 */
    @Test
    void iconIdOutOfRangeFallsBackToModulo() {
        int size = LIB.size();
        assertTrue(size > 0, "库不应为空");

        String wrapped = BuiltinIcons.nameForIconId(size);
        String wrappedThen = BuiltinIcons.nameForIconId(size + 3);

        assertNotNull(wrapped, "超范围编号不应映射到 null");
        assertTrue(LIB.contains(wrapped), "超范围编号应回落到库内的某个图标，实际为 " + wrapped);
        assertEquals(BuiltinIcons.nameForIconId(0), wrapped,
                "编号等于库大小时应回绕到 0 号图标");
        assertEquals(BuiltinIcons.nameForIconId(3), wrappedThen,
                "编号等于库大小+3 时应回绕到 3 号图标");
    }

    /** 同一编号多次解析结果必须稳定（导入时按 iconId 缓存，也依赖这一点）。 */
    @Test
    void mappingIsStableAcrossCalls() {
        for (int id : new int[]{0, 22, 48, 68, 100, 1000}) {
            assertEquals(BuiltinIcons.nameForIconId(id), BuiltinIcons.nameForIconId(id),
                    "编号 " + id + " 的映射应稳定");
        }
    }
}
