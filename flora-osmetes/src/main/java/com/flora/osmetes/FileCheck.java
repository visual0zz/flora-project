package com.flora.osmetes;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 一个可插拔的文件级检查项。
 * <p>
 * 每个检查项定义类自行报告它关心哪些文件后缀名（见 {@link #fileExtensions()}），
 * 引擎据此将匹配的文件分派给它。检查时必须报告详细的发现位置，且应尽量
 * 收集一个文件内的全部问题，而不是发现第一个就返回。
 * <p>
 * 新增检查项只需实现本接口并注册到 {@code Osmetes} 引擎即可，无需改动引擎
 * 的遍历与上报逻辑——这就是预留的扩展点。
 */
public interface FileCheck {

    /**
     * 该检查项的名称，用于 {@link CheckIssue#check()} 标识与日志。
     */
    String name();

    /**
     * 该检查项负责处理的文件后缀名集合（小写、含点，如 {@code ".java"}）。
     * <p>
     * 引擎只把后缀匹配的文件交给该检查项；返回空集合表示不参与文件级检查。
     */
    Set<String> fileExtensions();

    /**
     * 检查单个文件，把发现的问题追加到 {@code sink}。
     * <p>
     * 实现必须收集该文件的全部问题（除非无法继续读取），不得因发现一个
     * 问题而提前返回；引擎会遍历完所有文件、收集所有问题后才统一上报。
     *
     * @param file         待检查文件的绝对路径
     * @param relativeFile 相对检查根目录的规范化路径（使用 {@code /} 分隔）
     * @param sink         问题收集器，用于追加发现的问题
     */
    void check(Path file, String relativeFile, List<CheckIssue> sink);
}
