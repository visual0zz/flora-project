package com.flora.sanctum.core.icon;

import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 内置图标库（数据层资源，位于 core 模块 resources/icons/library/*.svg）。
 * <p>提供图标的名字列表与资源 URL，供渲染（app 的 SvgIcon）与数据导入（KDBX 内置图标映射）复用，
 * 使 core 不再反向依赖 app.ui。扫描逻辑在此实现，渲染（jsvg）仍留在 app。</p>
 */
public final class BuiltinIcons {

    private BuiltinIcons() {
    }

    /** 图标库资源根目录（classpath 下的 /icons/library/）。 */
    private static final String LIBRARY_PREFIX = "icons/library/";

    private static volatile List<String> CACHED_NAMES;

    /** 列出图标库中所有内置图标的名称（不含扩展名），按字母序返回（缓存）。 */
    public static List<String> names() {
        List<String> cached = CACHED_NAMES;
        if (cached == null) {
            synchronized (BuiltinIcons.class) {
                cached = CACHED_NAMES;
                if (cached == null) {
                    CACHED_NAMES = cached = scan();
                }
            }
        }
        return cached;
    }

    /** 取某个内置图标的资源 URL（用于渲染读取字节）；不存在返回 null。 */
    public static URL url(String name) {
        return BuiltinIcons.class.getResource("/" + LIBRARY_PREFIX + name + ".svg");
    }

    private static List<String> scan() {
        Module module = BuiltinIcons.class.getModule();
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
        URL dir = BuiltinIcons.class.getResource("/" + LIBRARY_PREFIX);
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
            java.nio.file.Path root = java.nio.file.Paths.get(dir.toURI());
            try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(root)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".svg"))
                        .forEach(p -> names.add(nameOf(LIBRARY_PREFIX + p.getFileName())));
            }
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
        }
    }

    private static String nameOf(String path) {
        return path.substring(LIBRARY_PREFIX.length(), path.length() - ".svg".length());
    }
}
