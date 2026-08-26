package com.flora.sanctum.app.ui;

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
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

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

    /** 图标库资源根目录（classpath 下的 /icons/library/）。 */
    private static final String LIBRARY_PREFIX = "icons/library/";

    /**
     * 列出图标库中所有内置图标的名称（不含扩展名），按字母序返回。
     * <p>
     * 运行时动态扫描 {@code /icons/library/*.svg}，因此向该目录新增 SVG 后无需修改代码即可出现在选择器中：
     * 命名模块（JPMS）用 {@link ModuleReference#open()} 的 {@link java.lang.module.ModuleReader} 枚举；
     * 无名模块（classpath / 测试）按资源 URL 是文件目录还是 jar 分别回退。
     */
    public static List<String> libraryIcons() {
        List<String> cached = CACHED_ICON_NAMES;
        if (cached == null) {
            synchronized (SvgIcon.class) {
                cached = CACHED_ICON_NAMES;
                if (cached == null) {
                    CACHED_ICON_NAMES = cached = scanLibrary();
                }
            }
        }
        return cached;
    }

    private static volatile List<String> CACHED_ICON_NAMES;

    private static List<String> scanLibrary() {
        Module module = SvgIcon.class.getModule();
        if (module != null && module.isNamed()) {
            return scanFromModule(module);
        }
        return scanFromClasspath();
    }

    private static List<String> scanFromModule(Module module) {
        List<String> names = new ArrayList<>();
        try {
            ModuleReference ref = module.getLayer().configuration()
                    .findModule(module.getName()).map(ResolvedModule::reference).orElseThrow();
            try (java.lang.module.ModuleReader reader = ref.open()) {
                reader.list().forEach(path -> {
                    if (path.startsWith(LIBRARY_PREFIX) && path.endsWith(".svg")) {
                        names.add(nameOf(path));
                    }
                });
            }
        } catch (Exception ignored) {
        }
        Collections.sort(names);
        return names;
    }

    private static List<String> scanFromClasspath() {
        List<String> names = new ArrayList<>();
        URL dir = SvgIcon.class.getResource("/" + LIBRARY_PREFIX);
        if (dir != null) {
            switch (dir.getProtocol()) {
                case "file" -> scanDirectory(dir, names);
                case "jar" -> scanJar(dir, names);
                default -> { }
            }
        }
        Collections.sort(names);
        return names;
    }

    private static void scanDirectory(URL dir, List<String> names) {
        try {
            Path root = Paths.get(dir.toURI());
            try (Stream<Path> stream = Files.list(root)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".svg"))
                        .forEach(p -> names.add(nameOf("icons/library/" + p.getFileName())));
            }
        } catch (IOException | java.net.URISyntaxException ignored) {
        }
    }

    private static void scanJar(URL dir, List<String> names) {
        String spec = dir.toString();
        int bang = spec.indexOf("!/");
        if (bang < 0) {
            return;
        }
        String jarPath = spec.substring("jar:file:".length(), bang);
        try (JarFile jar = new JarFile(jarPath)) {
            for (var entry = jar.entries(); entry.hasMoreElements();) {
                String path = entry.nextElement().getName();
                if (path.startsWith(LIBRARY_PREFIX) && path.endsWith(".svg")) {
                    names.add(nameOf(path));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static String nameOf(String path) {
        return path.substring(LIBRARY_PREFIX.length(), path.length() - ".svg".length());
    }

    /** 取一个已渲染的 SVG 图标（资源位于 /icons/&lt;name&gt;.svg）。 */
    public static Icon get(String name, int size) {
        String key = name + "@" + size;
        return CACHE.computeIfAbsent(key, k -> load(name, size));
    }

    /** 从 SVG 字节渲染图标（用于仓库内用户自定义 svg 图标），不缓存。 */
    public static Icon fromBytes(byte[] data, int size) {
        try (InputStream in = new java.io.ByteArrayInputStream(data)) {
            // 字节无真实 URI，用内存 URI 占位（jsvg 需要非空 URI）
            SVGDocument doc = new SVGLoader().load(in, URI.create("memory://icon"), LoaderContext.createDefault());
            return render(doc, size);
        } catch (Exception e) {
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
        try (InputStream in = SvgIcon.class.getResourceAsStream("/icons/" + name + ".svg")) {
            if (in == null) {
                return null;
            }
            URI uri = SvgIcon.class.getResource("/icons/" + name + ".svg").toURI();
            // jsvg 2.0 的 load 要求非空 LoaderContext（传 null 会在 SVGDocumentBuilder 内 NPE）
            SVGDocument doc = new SVGLoader().load(in, uri, LoaderContext.createDefault());
            return render(doc, size);
        } catch (Exception e) {
            return null;
        }
    }
}
