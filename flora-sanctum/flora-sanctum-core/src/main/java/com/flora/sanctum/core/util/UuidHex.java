package com.flora.sanctum.core.util;

import java.util.UUID;

/**
 * UUID 的 32 位无连字符 hex 形态（内存/JSON 存储统一用此形态，与磁盘路径的 hex 形态一致）。
 * <p>
 * 标准 {@link UUID#toString()} 产出 36 字符带连字符串（{@code 8-4-4-4-12}）；
 * 块内 JSON 的 {@code parent}/引用 {@code id} 等一律以 32 位 hex 存储，省去连字符、与路径形态对齐。
 * {@link #fromHex} 容错：既接受 32 位 hex，也接受标准 36 位带连字符串（兼容遗留/测试数据）。
 */
public final class UuidHex {

    private UuidHex() {
    }

    /** UUID → 32 位无连字符 hex（内存/JSON 存储形态）。 */
    public static String toHex(UUID u) {
        return u.toString().replace("-", "");
    }

    /** 容错解析：32 位 hex 或标准 36 位带连字符串。 */
    public static UUID fromHex(String s) {
        if (s == null) {
            throw new IllegalArgumentException("null uuid");
        }
        if (s.indexOf('-') >= 0) {
            return UUID.fromString(s);
        }
        if (s.length() != 32) {
            throw new IllegalArgumentException("bad uuid hex (expect 32 chars): " + s);
        }
        return UUID.fromString(s.substring(0, 8) + "-" + s.substring(8, 12) + "-"
                + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20));
    }

    /** 同 {@link #fromHex} 但非法/空输入返回 null（UI 静默降级用）。 */
    public static UUID fromHexOrNull(String s) {
        try {
            return fromHex(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
