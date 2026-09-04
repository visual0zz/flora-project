package com.flora.sanctum.core.icon;

import java.io.IOException;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 内置图标库（数据层资源，位于 core 模块 resources/icons/library/*.svg）。
 * <p>提供图标的名字列表与资源 URL，供渲染（app 的 SvgIcon）与数据导入（KDBX 内置图标映射）复用，
 * 扫描逻辑在 core 实现，渲染（jsvg）由 app 负责，保持 core 对 app 无依赖。</p>
 */
public final class BuiltinIcons {

    private BuiltinIcons() {
    }

    /** 图标库资源根目录（classpath 下的 /icons/library/）。 */
    private static final String LIBRARY_PREFIX = "icons/library/";

    // 类加载时扫描一次，结果不可变（库不应持有可清空的全局可变缓存）。
    private static final List<String> NAMES = Collections.unmodifiableList(scan());

    /** 列出图标库中所有内置图标的名称（不含扩展名），按字母序返回（不可变，类加载时扫描一次）。 */
    public static List<String> names() {
        return NAMES;
    }

    /** 取某个内置图标的资源 URL（用于渲染读取字节）；不存在返回 null。 */
    public static URL url(String name) {
        return BuiltinIcons.class.getResource("/" + LIBRARY_PREFIX + name + ".svg");
    }

    // 不感知打包形态：命名模块用 ModuleReader 枚举（JDK 屏蔽 jar/解压目录差异），
    // 未命名模块用类加载器的标准 URL 枚举（file:/jar: 由 JDK 处理）。
    private static List<String> scan() {
        List<String> names = new ArrayList<>();
        Module module = BuiltinIcons.class.getModule();
        if (module != null && module.isNamed()) {
            collectFromModule(module, names);
        } else {
            collectFromClasspath(names);
        }
        Collections.sort(names);
        return names;
    }

    private static void collectFromModule(Module module, List<String> names) {
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
    }

    private static void collectFromClasspath(List<String> names) {
        try {
            Enumeration<URL> resources = BuiltinIcons.class.getClassLoader().getResources(LIBRARY_PREFIX);
            while (resources.hasMoreElements()) {
                collectFromUrl(resources.nextElement(), names);
            }
        } catch (IOException ignored) {
        }
    }

    private static void collectFromUrl(URL url, List<String> names) {
        switch (url.getProtocol()) {
            case "file" -> collectFromDirectory(url, names);
            case "jar" -> collectFromJar(url, names);
            default -> { }
        }
    }

    private static void collectFromDirectory(URL url, List<String> names) {
        try {
            Path root = Paths.get(url.toURI());
            try (Stream<Path> stream = Files.list(root)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".svg"))
                        .forEach(p -> names.add(nameOf(LIBRARY_PREFIX + p.getFileName())));
            }
        } catch (Exception ignored) {
        }
    }

    // 用标准 JarURLConnection 取 jar 文件，避免手写 "jar:file:" 字符串切分。
    private static void collectFromJar(URL url, List<String> names) {
        try {
            JarURLConnection conn = (JarURLConnection) url.openConnection();
            try (JarFile jar = conn.getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String path = entries.nextElement().getName();
                    if (path.startsWith(LIBRARY_PREFIX) && path.endsWith(".svg")) {
                        names.add(nameOf(path));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String nameOf(String path) {
        return path.substring(LIBRARY_PREFIX.length(), path.length() - ".svg".length());
    }
}
