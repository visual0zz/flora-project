package com.flora.sanctum.app.ui;

import com.flora.sanctum.core.icon.BuiltinIcons;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;

/**
 * 加载并缓存 SVG 矢量图标，渲染为 Swing {@link ImageIcon}。
 * <p>
 * 用 jsvg 把 resources 下的 SVG 渲染到透明 BufferedImage，可按需缩放（矢量，任意尺寸不失真）。
 * 图标按名称+尺寸缓存。内置图标库的名字列表与资源由 {@link BuiltinIcons}（core，数据层）提供，
 * 本类只负责渲染。</p>
 */
public final class SvgIcon {

    private static final Logger LOG = LoggerFactory.getLogger(SvgIcon.class);

    private SvgIcon() {
    }

    private static final Map<String, Icon> CACHE = new HashMap<>();

    /**
     * 列出图标库中所有内置图标的名称（不含扩展名），按字母序返回。
     * 委托 core 的 {@link BuiltinIcons}（资源位于 core 模块 /icons/library/*.svg）。
     */
    public static List<String> libraryIcons() {
        return BuiltinIcons.names();
    }

    /** 取一个已渲染的 SVG 图标（资源位于 /icons/&lt;name&gt;.svg）。 */
    public static Icon get(String name, int size) {
        String key = name + "@" + size;
        return CACHE.computeIfAbsent(key, k -> load(name, size));
    }

    /** 取一个已注册的 ui 图标（枚举成员，编译期校验拼写；资源缺失返回 null）。 */
    public static Icon get(UiIcon icon, int size) {
        return get(icon.path(), size);
    }

    /** 从 SVG 字节渲染图标（用于仓库内用户自定义 svg 图标），不缓存。 */
    public static Icon fromBytes(byte[] data, int size) {
        try (InputStream in = new java.io.ByteArrayInputStream(data)) {
            // 字节无真实 URI，用内存 URI 占位（jsvg 需要非空 URI）
            SVGDocument doc = new SVGLoader().load(in, URI.create("memory://icon"), LoaderContext.createDefault());
            return render(doc, size);
        } catch (Exception e) {
            LOG.warn("Failed to render custom SVG icon ({} bytes): {}", data.length, e.getMessage());
            return null;
        }
    }

    private static Icon render(SVGDocument doc, int size) {
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
    }

    private static Icon load(String name, int size) {
        try {
            // 内置图标库资源在 core 模块（/icons/library/<name>.svg）；
            // 操作/类型图标资源在 app 模块（/icons/button/<name>.svg、/icons/item/<name>.svg）。
            URL resUrl = name.startsWith("library/")
                    ? BuiltinIcons.url(name.substring("library/".length()))
                    : SvgIcon.class.getResource("/icons/" + name + ".svg");
            if (resUrl == null) {
                LOG.warn("Icon resource not found: {}", name);
                return null;
            }
            try (InputStream in = resUrl.openStream()) {
                SVGDocument doc = new SVGLoader().load(in, resUrl.toURI(), LoaderContext.createDefault());
                return render(doc, size);
            }
        } catch (IOException | java.net.URISyntaxException e) {
            LOG.warn("Failed to load icon {}: {}", name, e.getMessage());
            return null;
        }
    }
}
