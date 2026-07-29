/**
 * 配置加载系统。
 * <p>支持从文件、类路径等多种来源加载配置，可多来源合并，并支持通过配置
 * 自身指定额外路径加载更多配置文件。利用 {@code com.flora.codec} 包中的
 * 解析工具（JSON/YAML/TOML/Properties）自动识别格式。</p>
 *
 * <p>核心接口：{@link com.flora.runtime.config.ConfigLoader}、
 * {@link com.flora.runtime.config.ConfigSource}、
 * {@link com.flora.runtime.config.ConfigMap}。</p>
 */
package com.flora.runtime.config;
