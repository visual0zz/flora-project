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
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 按名称里的前导 {@code NN-} 数字（KeePass 的 IconID）排序的图标名列表，类加载时构建一次。
     * 库里所有文件都以两位 IconID 前缀命名（如 {@code 01-earth.svg}），据此即可把 KDBX 的 IconID
     * 精确映射到对应图标；无数字前缀的名字排在所有带前缀名字之后（按字母序）。
     */
    private static final List<String> ORDERED_BY_ID = buildOrderedById();

    /** IconID → 图标名 的精确映射（仅含带数字前缀的名字）。 */
    private static final Map<Integer, String> ID_TO_NAME = buildIdToName();

    /** 列出图标库中所有内置图标的名称（不含扩展名），按字母序返回（不可变，类加载时扫描一次）。 */
    public static List<String> names() {
        return NAMES;
    }

    /**
     * 把 KeePass 的 IconID 映射到本库里的内置图标名：
     * 编号落在库的范围内（存在同名前缀的文件）时精确命中；
     * 超出范围（或该编号缺图标）时回退为对库大小取模，保证总能落到一个有效图标。
     *
     * @param iconId KeePass 条目的 IconID（非负）
     * @return 图标名；库为空时返回 null
     */
    public static String nameForIconId(int iconId) {
        String direct = ID_TO_NAME.get(iconId);
        if (direct != null) {
            return direct;
        }
        if (ORDERED_BY_ID.isEmpty()) {
            return null;
        }
        return ORDERED_BY_ID.get(Math.floorMod(iconId, ORDERED_BY_ID.size()));
    }

    private static List<String> buildOrderedById() {
        List<String> ordered = new ArrayList<>(NAMES);
        // 无数字前缀的名字归到末尾参与取模（不能让 -1 排到编号 0 之前，否则取模会先落到它们身上）
        ordered.sort(Comparator.comparingInt(BuiltinIcons::sortKey)
                .thenComparing(Comparator.naturalOrder()));
        return Collections.unmodifiableList(ordered);
    }

    /** 排序键：有 IconID 前缀用其编号，否则排在最后（Integer.MAX_VALUE）。 */
    private static int sortKey(String name) {
        int prefix = iconIdPrefix(name);
        return prefix < 0 ? Integer.MAX_VALUE : prefix;
    }

    private static Map<Integer, String> buildIdToName() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (String name : NAMES) {
            int prefix = iconIdPrefix(name);
            if (prefix >= 0) {
                map.put(prefix, name);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /** 取名称前导数字（KeePass IconID 前缀）；无数字前缀返回 -1。 */
    private static int iconIdPrefix(String name) {
        int i = 0;
        while (i < name.length() && name.charAt(i) >= '0' && name.charAt(i) <= '9') {
            i++;
        }
        if (i == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(name.substring(0, i));
        } catch (NumberFormatException ignored) {
            return -1;
        }
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
