package com.flora.sanctum.app.io.importer.kdbx;

import com.flora.sanctum.app.io.importer.ImportException;
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
import java.util.List;

/**
 * 解析 KDBX4 内层（内层头 + 内层 XML）。
 * <p>内层头给出内层随机流算法与密钥；XML 中 {@code Protected="True"} 的字段值先 Base64 解码，
 * 再用内层流（顺序密钥流）异或还原明文。</p>
 */
final class KdbxXml {

    private KdbxXml() {
    }

    static KdbxDocument parse(byte[] inner) throws ImportException {
        int p = 0;
        int innerStreamId = 1;            // 默认 Salsa20
        byte[] innerKey = new byte[32];
        while (p + 5 <= inner.length) {
            int id = inner[p++] & 0xff;
            int len = readLe32(inner, p);
            p += 4;
            if (p + len > inner.length) {
                throw new ImportException("内层头越界");
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
            throw new ImportException("内层 XML 缺失");
        }
        byte[] xmlBytes = new byte[inner.length - p];
        System.arraycopy(inner, p, xmlBytes, 0, xmlBytes.length);

        KdbxStreamCipher stream = new KdbxStreamCipher(innerStreamId, innerKey);
        return parseXml(xmlBytes, stream);
    }

    private static KdbxDocument parseXml(byte[] xmlBytes, KdbxStreamCipher stream) throws ImportException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
            Element root = doc.getDocumentElement();
            Element rootGroup = firstChild(root, "Root");
            if (rootGroup == null) {
                throw new ImportException("KDBX 缺少 <Root>");
            }
            Element group = firstChild(rootGroup, "Group");
            if (group == null) {
                throw new ImportException("KDBX 缺少根 <Group>");
            }
            KdbxDocument.KdbxGroup g = parseGroup(group, stream);
            return new KdbxDocument(g);
        } catch (ImportException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportException("KDBX XML 解析失败", e);
        }
    }

    private static KdbxDocument.KdbxGroup parseGroup(Element groupEl, KdbxStreamCipher stream) {
        KdbxDocument.KdbxGroup g = new KdbxDocument.KdbxGroup();
        g.name = textOfChild(groupEl, "Name");
        g.uuid = uuidText(groupEl);
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
        for (Element s : childElements(entryEl)) {
            if (!"String".equals(s.getTagName())) {
                continue;
            }
            String key = textOfChild(s, "Key");
            Element valueEl = firstChild(s, "Value");
            if (key == null || valueEl == null) {
                continue;
            }
            String raw = valueEl.getTextContent();
            boolean prot = "True".equalsIgnoreCase(s.getAttribute("Protected"));
            String value = prot ? stream.decrypt(raw) : raw;
            e.fields.put(key, new KdbxDocument.KdbxField(value, prot));
        }
        Element times = firstChild(entryEl, "Times");
        if (times != null) {
            e.creationTime = parseTime(textOfChild(times, "CreationTime"));
            e.lastModTime = parseTime(textOfChild(times, "LastModificationTime"));
        }
        KdbxDocument.KdbxField title = e.fields.get("Title");
        e.name = title == null ? "" : title.value;
        return e;
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
