package com.flora.sanctum.app.ui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * 加载并缓存 SVG 矢量图标，渲染为 Swing {@link ImageIcon}。
 * <p>
 * 用 jsvg 把 resources/icons 下的 SVG 渲染到透明 BufferedImage，
 * 可按需缩放（矢量，任意尺寸不失真）。图标按名称+尺寸缓存。
 */
public final class SvgIcon {

    private SvgIcon() {
    }

    private static final Map<String, Icon> CACHE = new HashMap<>();

    /** 取一个已渲染的 SVG 图标（资源位于 /icons/&lt;name&gt;.svg）。 */
    public static Icon get(String name, int size) {
        String key = name + "@" + size;
        return CACHE.computeIfAbsent(key, k -> load(name, size));
    }

    private static Icon load(String name, int size) {
        try (InputStream in = SvgIcon.class.getResourceAsStream("/icons/" + name + ".svg")) {
            if (in == null) {
                return null;
            }
            URI uri = SvgIcon.class.getResource("/icons/" + name + ".svg").toURI();
            // jsvg 2.0 的 load 要求非空 LoaderContext（传 null 会在 SVGDocumentBuilder 内 NPE）
            SVGDocument doc = new SVGLoader().load(in, uri, LoaderContext.createDefault());
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.scale((double) size / doc.size().width, (double) size / doc.size().height);
                doc.render(null, g, new ViewBox(doc.size()));
            } finally {
                g.dispose();
            }
            return new ImageIcon(img);
        } catch (Exception e) {
            return null;
        }
    }
}
