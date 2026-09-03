package com.flora.sanctum.kdbx;

import com.flora.sanctum.kdbx.internal.KdbxXml;
import com.flora.root.runtime.log.Logger;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
     * 回归：KeePassXC 把自定义图标写成 {@code <Icon>}（而非 KeePass 2.x 规范的 {@code <CustomIcon>}），
     * 且 UUID 以子元素 {@code <UUID>} 给出。早期实现只认 {@code <CustomIcon>}，导致 KeePassXC 导出的
     * 图标整批被丢弃、导入后「自定义图标缺失」告警刷屏。此处验证 {@code <Icon>} 也能被提取并正确引用。
     */
    @Test
    void parseCustomIconsReadsKeePassXcIconElement() throws Exception {
        byte[] uuid16 = hexToBytes(CUSTOM_UUID);
        String uuidB64 = Base64.getEncoder().encodeToString(uuid16);
        String pngB64 = Base64.getEncoder().encodeToString(PNG_1X1);
        // KeePassXC 实际格式：<Icon> 包裹 <UUID>（子元素）与 <Data>
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<KeePassFile><Meta><CustomIcons>"
                + "<Icon><UUID>" + uuidB64 + "</UUID><Data>" + pngB64 + "</Data></Icon>"
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

        assertFalse(doc.customIcons.isEmpty(), "KeePassXC 的 <Icon> 也应被解析");
        assertTrue(doc.customIcons.containsKey(CUSTOM_UUID), "应以 hex uuid 为 key");
        assertArrayEquals(PNG_1X1, doc.customIcons.get(CUSTOM_UUID), "图标字节应原样提取");
        assertEquals(CUSTOM_UUID, doc.root.entries.get(0).customIconUuid,
                "条目经 <CustomIconUUID> 引用应解析成功");
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

    /**
     * 诊断打点回归：残缺的 {@code <CustomIcon>}（如缺少 {@code <Data>} 子元素）被跳过时，
     * 必须经由注入的日志器记录告警，而不是像早期实现那样静默吞掉
     * （否则导入后「自定义图标缺失」会让人无从查起）。合法图标仍应正常保留。
     */
    @Test
    void parseCustomIconsLogsMalformedIcon() throws Exception {
        byte[] uuid16 = hexToBytes(CUSTOM_UUID);
        String uuidB64 = Base64.getEncoder().encodeToString(uuid16);
        String pngB64 = Base64.getEncoder().encodeToString(PNG_1X1);
        // 一个合法图标 + 一个缺少 <Data> 的残缺图标（真实文件偶发损坏时会出现）
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<KeePassFile><Meta><CustomIcons>"
                + "<CustomIcon UUID=\"" + uuidB64 + "\"><Data>" + pngB64 + "</Data></CustomIcon>"
                + "<CustomIcon UUID=\"" + uuidB64 + "\"></CustomIcon>"
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

        CapturingLogger log = new CapturingLogger();
        KdbxDocument doc = KdbxXml.parse(inner, log);

        // 合法图标保留，残缺图标丢弃
        assertEquals(1, doc.customIcons.size(), "残缺图标不应进入 customIcons");
        assertTrue(doc.customIcons.containsKey(CUSTOM_UUID), "合法图标应被保留");
        // 且必须留下可检索的日志（本次诊断打点的核心目的）
        assertTrue(log.warnings.stream().anyMatch(w -> w.contains("解析跳过") && w.contains(CUSTOM_UUID)),
                "残缺图标应经注入的日志器记录告警（含图标 UUID 与「解析跳过」），便于定位");
    }

    /** 仅记录 warn/info 的轻量 Logger 实现，供测试断言诊断日志内容。 */
    private static final class CapturingLogger implements Logger {
        final List<String> warnings = new CopyOnWriteArrayList<>();
        final List<String> infos = new CopyOnWriteArrayList<>();

        private static String fmt(String format, Object... args) {
            if (args == null || args.length == 0) {
                return format;
            }
            StringBuilder sb = new StringBuilder(format);
            for (Object a : args) {
                int i = sb.indexOf("{}");
                if (i < 0) {
                    sb.append(' ').append(a);
                } else {
                    sb.replace(i, i + 2, String.valueOf(a));
                }
            }
            return sb.toString();
        }

        @Override public String getName() { return "capturing"; }
        @Override public boolean isTraceEnabled() { return false; }
        @Override public boolean isDebugEnabled() { return false; }
        @Override public boolean isInfoEnabled() { return true; }
        @Override public boolean isWarnEnabled() { return true; }
        @Override public boolean isErrorEnabled() { return true; }
        @Override public boolean isFatalEnabled() { return true; }

        @Override public void trace(String msg) {}
        @Override public void trace(String format, Object... args) {}
        @Override public void trace(String msg, Throwable t) {}
        @Override public void trace(java.util.function.Supplier<String> m) {}

        @Override public void debug(String msg) {}
        @Override public void debug(String format, Object... args) {}
        @Override public void debug(String msg, Throwable t) {}
        @Override public void debug(java.util.function.Supplier<String> m) {}

        @Override public void info(String msg) { infos.add(msg); }
        @Override public void info(String format, Object... args) { infos.add(fmt(format, args)); }
        @Override public void info(String msg, Throwable t) { infos.add(msg); }
        @Override public void info(java.util.function.Supplier<String> m) { infos.add(m.get()); }

        @Override public void warn(String msg) { warnings.add(msg); }
        @Override public void warn(String format, Object... args) { warnings.add(fmt(format, args)); }
        @Override public void warn(String msg, Throwable t) { warnings.add(msg); }
        @Override public void warn(java.util.function.Supplier<String> m) { warnings.add(m.get()); }

        @Override public void error(String msg) {}
        @Override public void error(String format, Object... args) {}
        @Override public void error(String msg, Throwable t) {}
        @Override public void error(java.util.function.Supplier<String> m) {}

        @Override public void fatal(String msg) {}
        @Override public void fatal(String format, Object... args) {}
        @Override public void fatal(String msg, Throwable t) {}
        @Override public void fatal(java.util.function.Supplier<String> m) {}
    }
}
