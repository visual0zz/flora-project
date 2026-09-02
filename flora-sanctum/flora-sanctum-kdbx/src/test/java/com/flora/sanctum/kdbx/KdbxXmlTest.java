package com.flora.sanctum.kdbx;

import com.flora.sanctum.kdbx.internal.KdbxXml;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内层 XML（{@link KdbxXml}）解析测试：自定义图标（CustomIcons）提取、UUID 属性读取、
 * 条目经 {@code <CustomIconUUID>} 引用自定义图标。
 */
class KdbxXmlTest {

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

    /**
     * KeePass/KeePassXC 把图标 {@code <Data>} 的 base64 按 MIME 习惯折行（每 64/76 字符插换行）。
     * Java 的 {@link Base64#getDecoder()} 拒绝换行符，若直接 decode 会抛异常并被静默吞掉，
     * 结果整份文件的自定义图标全部丢失（图标库为空、条目无图标，且无任何报错）。
     */
    @Test
    void parseCustomIconsToleratesWrappedBase64() throws Exception {
        byte[] uuid16 = hexToBytes(CUSTOM_UUID);
        String uuidB64 = Base64.getEncoder().encodeToString(uuid16);
        // 模拟真实文件：base64 每 64 字符插一个换行（含首尾缩进/换行等空白）
        String pngB64Wrapped = "\n  " + wrap(Base64.getEncoder().encodeToString(PNG_1X1), 64) + "\n";
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<KeePassFile><Meta><CustomIcons>"
                + "<CustomIcon UUID=\"" + uuidB64 + "\"><Data>" + pngB64Wrapped + "</Data></CustomIcon>"
                + "</CustomIcons></Meta>"
                + "<Root><Group><UUID>" + Base64.getEncoder().encodeToString(new byte[16]) + "</UUID>"
                + "<Name>Root</Name>"
                + "<Entry><UUID>" + Base64.getEncoder().encodeToString(new byte[16]) + "</UUID>"
                + "<CustomIconUUID>" + uuidB64 + "</CustomIconUUID>"
                + "<String><Key>Title</Key><Value>t</Value></String>"
                + "<String><Key>Password</Key><Value>p</Value></String>"
                + "</Entry></Group></Root></KeePassFile>";
        byte[] innerHeader = {
                0x01, 0x04, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00
        };
        byte[] inner = concat(innerHeader, xml.getBytes(StandardCharsets.UTF_8));

        KdbxDocument doc = KdbxXml.parse(inner);

        assertTrue(doc.customIcons.containsKey(CUSTOM_UUID),
                "折行 base64 的自定义图标也应被解析（不应静默丢弃）");
        assertArrayEquals(PNG_1X1, doc.customIcons.get(CUSTOM_UUID), "折行 base64 解码后字节应一致");
    }

    /** 把字符串按 width 折行（模拟 base64 MIME 折行）。 */
    private static String wrap(String s, int width) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += width) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(s, i, Math.min(i + width, s.length()));
        }
        return sb.toString();
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
