package com.flora.ramet.engine;

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
     * @param path    include 路径：相对路径以 {@code fromKey} 所在目录为基准，
     *                以 {@code '/'} 开头视为相对于仓库根的绝对路径
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
     * 此处 key 即直接用作定位标识，不做路径换算。
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
                // 内存仓库无真实根目录：绝对路径（以 '/' 开头）去掉前导 '/'
                // 后直接作为 key，相对路径原样作为 key。
                String key = path.startsWith("/") ? path.substring(1) : path;
                return key.replace('\\', '/');
            }
        };
    }
}
