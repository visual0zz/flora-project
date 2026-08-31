package com.flora.sanctum.kdbx.internal;

import com.flora.sanctum.kdbx.KdbxDocument;
import com.flora.sanctum.kdbx.KdbxReadException;
import com.flora.sanctum.kdbx.KdbxReadException.Stage;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 解析 KDBX 内层（内层头 + 内层 XML）。
 * <p>内层头给出内层随机流算法与密钥；XML 中 {@code Protected="True"} 的字段值先 Base64 解码，
 * 再用内层流（顺序密钥流）异或还原明文。</p>
 */
public final class KdbxXml {

    private KdbxXml() {
    }

    public static KdbxDocument parse(byte[] inner) throws KdbxReadException {
        int p = 0;
        int innerStreamId = 1;            // 默认 Salsa20
        byte[] innerKey = new byte[32];
        while (p + 5 <= inner.length) {
            int id = inner[p++] & 0xff;
            int len = readLe32(inner, p);
            p += 4;
            if (p + len > inner.length) {
                throw new KdbxReadException(Stage.INNER, "内层头越界");
            }
            byte[] field = new byte[len];
            System.arraycopy(inner, p, field, 0, len);
            p += len;
            if (id == 0) { // End
                break;
            }
            if (id == 1) {
                innerStreamId = readLe32(field, 0);
            } else if (id == 2) {
                innerKey = field;
            }
        }
        if (p >= inner.length) {
            throw new KdbxReadException(Stage.INNER, "内层 XML 缺失");
        }
        byte[] xmlBytes = new byte[inner.length - p];
        System.arraycopy(inner, p, xmlBytes, 0, xmlBytes.length);

        KdbxStreamCipher stream = new KdbxStreamCipher(innerStreamId, innerKey);
        return parseXml(xmlBytes, stream);
    }

    /** KDBX2/3 入口：内层随机流算法与密钥来自外层头部字段（无 TLV 内层头），直接解析 XML。 */
    public static KdbxDocument parseInner(byte[] xmlBytes, KdbxStreamCipher stream) throws KdbxReadException {
        return parseXml(xmlBytes, stream);
    }

    static KdbxDocument parseXml(byte[] xmlBytes, KdbxStreamCipher stream) throws KdbxReadException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
            Element root = doc.getDocumentElement();
            Map<String, byte[]> customIcons = parseCustomIcons(root);
            Element rootGroup = firstChild(root, "Root");
            if (rootGroup == null) {
                throw new KdbxReadException(Stage.XML, "KDBX 缺少 <Root>");
            }
            Element group = firstChild(rootGroup, "Group");
            if (group == null) {
                throw new KdbxReadException(Stage.XML, "KDBX 缺少根 <Group>");
            }
            KdbxDocument.KdbxGroup g = parseGroup(group, stream);
            return new KdbxDocument(g, customIcons);
        } catch (KdbxReadException e) {
            throw e;
        } catch (Exception e) {
            throw new KdbxReadException(Stage.XML, "KDBX XML 解析失败", e);
        }
    }

    private static KdbxDocument.KdbxGroup parseGroup(Element groupEl, KdbxStreamCipher stream) {
        KdbxDocument.KdbxGroup g = new KdbxDocument.KdbxGroup();
        g.name = textOfChild(groupEl, "Name");
        g.uuid = uuidText(groupEl);
        readIconRef(groupEl, v -> g.iconId = v, v -> g.customIconUuid = v);
        for (Element e : childElements(groupEl)) {
            String tag = e.getTagName();
            if ("Entry".equals(tag)) {
                g.entries.add(parseEntry(e, stream));
            } else if ("Group".equals(tag)) {
                g.groups.add(parseGroup(e, stream));
            }
        }
        return g;
    }

    private static KdbxDocument.KdbxEntry parseEntry(Element entryEl, KdbxStreamCipher stream) {
        KdbxDocument.KdbxEntry e = new KdbxDocument.KdbxEntry();
        e.uuid = uuidText(entryEl);
        readIconRef(entryEl, v -> e.iconId = v, v -> e.customIconUuid = v);
        // 内层随机流的密钥流在所有受保护字段上按 XML 文档顺序连续推进。
        // 因此必须严格按文档顺序遍历条目子元素：直接 <String> 子元素的受保护值既解密又保留；
        // <History> 内的历史条目同样含受保护字段，需推进密钥流但不保留其内容。
        // 注意：活动条目的 <Password> 等 <String> 可能位于 <History> 之后，故不能先集中处理活动字段。
        for (Element child : childElements(entryEl)) {
            String tag = child.getTagName();
            if ("String".equals(tag)) {
                String key = textOfChild(child, "Key");
                Element valueEl = firstChild(child, "Value");
                if (key == null || valueEl == null) {
                    continue;
                }
                String raw = valueEl.getTextContent();
                boolean prot = "True".equalsIgnoreCase(valueEl.getAttribute("Protected"));
                String value = prot ? stream.decrypt(raw) : raw;
                e.fields.put(key, new KdbxDocument.KdbxField(value, prot));
            } else if ("History".equals(tag)) {
                consumeProtectedStream(child, stream);
            }
        }
        Element times = firstChild(entryEl, "Times");
        if (times != null) {
            e.creationTime = parseTime(textOfChild(times, "CreationTime"));
            e.lastModificationTime = parseTime(textOfChild(times, "LastModificationTime"));
        }
        KdbxDocument.KdbxField title = e.fields.get("Title");
        e.name = title == null ? "" : title.value;
        return e;
    }

    /** 解析文档级自定义图标（Meta/CustomIcons）：UUID hex → 原始图像字节。不消耗内层密钥流。 */
    private static Map<String, byte[]> parseCustomIcons(Element root) {
        Element meta = firstChild(root, "Meta");
        if (meta == null) {
            return Map.of();
        }
        Element ci = firstChild(meta, "CustomIcons");
        if (ci == null) {
            return Map.of();
        }
        Map<String, byte[]> map = new LinkedHashMap<>();
        for (Element iconEl : childElements(ci)) {
            if (!"CustomIcon".equals(iconEl.getTagName())) {
                continue;
            }
            // KeePass 2.x 把图标 UUID 放在 <CustomIcon> 的 UUID 属性（base64）上；
            // 个别导出工具可能写作子元素 <UUID>，此处两者都兼容。
            String uuid = uuidFromBase64(iconEl.getAttribute("UUID").trim());
            if (uuid == null) {
                uuid = uuidText(iconEl);
            }
            Element dataEl = firstChild(iconEl, "Data");
            if (uuid == null || dataEl == null) {
                continue;
            }
            try {
                byte[] data = Base64.getDecoder().decode(dataEl.getTextContent().trim());
                map.put(uuid, data);
            } catch (Exception ignored) {
            }
        }
        return map;
    }

    /** 读取分组/条目上的图标引用：<IconID>（内置索引）与 <CustomIconUUID>（自定义 UUID hex）。 */
    private static void readIconRef(Element el, IntConsumer iconIdSetter,
                                    Consumer<String> customSetter) {
        Element iconIdEl = firstChild(el, "IconID");
        if (iconIdEl != null) {
            try {
                iconIdSetter.accept(Integer.parseInt(iconIdEl.getTextContent().trim()));
            } catch (Exception ignored) {
            }
        }
        Element cuiEl = firstChild(el, "CustomIconUUID");
        if (cuiEl != null) {
            String uuid = uuidFromBase64(cuiEl.getTextContent().trim());
            if (uuid != null) {
                customSetter.accept(uuid);
            }
        }
    }

    /** 将 KeePass 的 base64 UUID 文本解码为 32 位 hex 串。 */
    private static String uuidFromBase64(String b64) {
        if (b64 == null || b64.isBlank()) {
            return null;
        }
        try {
            byte[] b = Base64.getDecoder().decode(b64.trim());
            StringBuilder sb = new StringBuilder();
            for (byte x : b) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 仅推进内层流密钥流：对子树内所有受保护 <Value> 按文档顺序解密（结果丢弃）。 */
    private static void consumeProtectedStream(Element root, KdbxStreamCipher stream) {
        if ("Value".equals(root.getTagName())
                && "True".equalsIgnoreCase(root.getAttribute("Protected"))) {
            stream.decrypt(root.getTextContent());
            return;
        }
        for (Element c : childElements(root)) {
            consumeProtectedStream(c, stream);
        }
    }

    private static Long parseTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String uuidText(Element el) {
        String t = textOfChild(el, "UUID");
        if (t == null) {
            return null;
        }
        try {
            byte[] b = Base64.getDecoder().decode(t.trim());
            StringBuilder sb = new StringBuilder();
            for (byte x : b) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (Exception ignored) {
            return t;
        }
    }

    private static String textOfChild(Element el, String tag) {
        Element c = firstChild(el, tag);
        return c == null ? null : c.getTextContent();
    }

    private static Element firstChild(Element el, String tag) {
        for (Element e : childElements(el)) {
            if (tag.equals(e.getTagName())) {
                return e;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element el) {
        List<Element> out = new ArrayList<>();
        NodeList nl = el.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                out.add((Element) n);
            }
        }
        return out;
    }

    private static int readLe32(byte[] b, int off) {
        return (b[off] & 0xff) | (b[off + 1] & 0xff) << 8 | (b[off + 2] & 0xff) << 16 | (b[off + 3] & 0xff) << 24;
    }
}
