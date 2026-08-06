package com.flora.ramet.engine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 模板仓库：按 key 提供已解析的 {@link Template}。
 *
 * <p>渲染过程中遇到 {@code <#include "...">} 时，引擎不再持有预编译好的 Map，
 * 而是通过本接口按需拉取——入口模板与子模板因此走同一条「源 → 解析」管线。
 * 缓存、文件读写、路径解析都由实现方负责。</p>
 */
public interface TemplateRepository {

    /**
     * 按 key 加载已解析模板。找不到时抛 {@link CodeGenException}。
     */
    Template load(String key) throws CodeGenException;

    /**
     * 将 include 路径解析为仓库 key。
     *
     * @param fromKey 发起 include 的模板 key（可为 {@code null}，按根目录解析）
     * @param path    include 路径：操作系统绝对路径（平台相关：Unix 以 {@code '/'}
     *                开头，Windows 以盘符开头）原样作为 key；否则为相对于
     *                {@code fromKey} 所在目录的相对路径
     * @return 目标模板的 key
     */
    String resolve(String fromKey, String path) throws CodeGenException;

    /**
     * 空仓库：任何加载/解析都报「未找到」。适用于没有任何 include 的入口模板。
     */
    static TemplateRepository none() {
        return new TemplateRepository() {
            @Override
            public Template load(String key) {
                throw new CodeGenException("#include 未找到模板: " + key);
            }

            @Override
            public String resolve(String fromKey, String path) {
                throw new CodeGenException("#include 未找到模板: " + path);
            }
        };
    }

    /**
     * 内存仓库：从固定的 key→Template 映射构建，不可变。用于测试与纯内存场景。
     * 此处 key 即直接用作定位标识，不做真实文件换算。
     */
    static TemplateRepository from(Map<String, Template> map) {
        return new TemplateRepository() {
            @Override
            public Template load(String key) {
                Template t = map.get(key);
                if (t == null) throw new CodeGenException("#include 未找到模板: " + key);
                return t;
            }

            @Override
            public String resolve(String fromKey, String path) {
                // 内存仓库以 Map 自身为根。操作系统绝对路径（平台相关）去掉根
                // （盘符或前导 '/'）后作为相对于该根的 key；前导 '/' 属测试约定，
                // 同样视为相对于 Map 根；其余相对路径原样作为 key。
                Path p = Paths.get(path);
                if (p.isAbsolute()) {
                    String root = p.getRoot() != null ? p.getRoot().toString() : "";
                    return p.toString().substring(root.length()).replace('\\', '/');
                }
                if (path.startsWith("/")) {
                    return path.substring(1).replace('\\', '/');
                }
                return path.replace('\\', '/');
            }
        };
    }
}
