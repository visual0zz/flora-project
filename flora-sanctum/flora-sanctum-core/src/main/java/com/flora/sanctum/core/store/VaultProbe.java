package com.flora.sanctum.core.store;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 探测目录是否已是 Sanctum 仓库（见设计 04"存储布局"）。
 * <p>
 * 判定依据为目录内部是否含有特征目录：单字符（十六进制）分片目录与 {@code lib/}。
 * 仅做轻量目录探测，不解析块内容；无法列举时视为无特征目录。
 * <p>
 * 本类是「目录是否已是仓库」判据的唯一来源：新建（{@code Sanctum.createAndUnlock}）
 * 与 GUI 预检共用，避免逻辑重复与判据漂移。
 */
public final class VaultProbe {

    private VaultProbe() {
    }

    /** 返回命中的特征目录名列表（用于拒绝时报告具体原因）；不含则返回空列表。 */
    public static List<String> markers(Path dir) {
        List<String> markers = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return markers;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path child : ds) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                String name = child.getFileName().toString();
                if ("lib".equalsIgnoreCase(name)) {
                    markers.add("lib/");
                } else if (name.length() == 1 && name.matches("[0-9a-fA-F]")) {
                    markers.add(name + "/");
                }
            }
        } catch (IOException ignore) {
            // 无法列举则视为无特征目录
        }
        return markers;
    }

    /** 目录是否疑似已有 Sanctum 仓库（含任一特征目录）。 */
    public static boolean isVault(Path dir) {
        return !markers(dir).isEmpty();
    }
}
