package com.flora.sanctum.app.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 校验 {@link UiIcon} 枚举与 {@code /icons/button/}、{@code /icons/item/} 两目录的 SVG 文件
 * 并集一一对应（一个不多一个不少）。
 * <p>新增/删除图标但忘记同步枚举（或反之）时，本测试失败——在测试阶段即暴露，而非运行时静默空白。</p>
 */
class UiIconTest {

    /** 扫描 classpath 下 /icons/button/ 与 /icons/item/ 两目录的 svg 文件名并集（不含扩展名）。 */
    private static Set<String> files() {
        try {
            Set<String> all = new java.util.HashSet<>();
            for (String dir : new String[]{"/icons/button/", "/icons/item/"}) {
                var url = SvgIcon.class.getResource(dir);
                if (url == null) {
                    continue;
                }
                try (Stream<Path> stream = Files.list(Path.of(url.toURI()))) {
                    stream.filter(p -> p.getFileName().toString().endsWith(".svg"))
                            .map(p -> p.getFileName().toString().replaceFirst("\\.svg$", ""))
                            .forEach(all::add);
                }
            }
            return all;
        } catch (Exception e) {
            throw new IllegalStateException("扫描 /icons/button/ 或 /icons/item/ 失败", e);
        }
    }

    private static Set<String> enums() {
        return Stream.of(UiIcon.values()).map(UiIcon::fileName).collect(Collectors.toSet());
    }

    @Test
    void uiIconsMatchEnumExactly() {
        assertEquals(files(), enums(),
                "button/item 图标文件与 UiIcon 枚举不一致（文件或枚举多/少需同步）");
    }
}
