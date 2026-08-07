package com.flora.osmetes;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
 * <p>
 * 检查项级配置通过 {@link #configure(Map)} 下发：引擎在扫描前按各检查项的
 * {@link #name()} 划分命名空间，把已剥离前缀、仅属于自己的配置子集交给每个检查项；
 * 键的含义由各个检查项自行约定，引擎只负责按前缀路由、不解析子键的具体含义。
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
     * 接收检查项级配置。
     * <p>
     * 引擎在扫描开始前，按本检查项的 {@link #name()} 作为前缀，从通用配置表中划分出
     * 属于本检查项、<b>已剥离前缀</b>的子集（键形如 {@code "<name>.<subKey>"}，传入时
     * 仅剩 {@code <subKey>}），然后才调用本方法。因此各检查项只需认自己的裸键
     * （如 {@code allowed}），无需感知顶层命名空间，也看不到其它检查项的配置；未知子键
     * 直接忽略。默认实现为空（无需配置的检查项可不变更），因此外部 SPI 实现的既有
     * 检查项无需修改即可接入。
     *
     * @param properties 已剥离 {@code name()} 前缀、仅属于本检查项的配置子集，键为裸子键
     */
    default void configure(Map<String, String> properties) {
    }

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
