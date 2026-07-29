/**
 * 配置加载系统。
 *
 * <h3>设计原则：无预留命名空间</h3>
 * <p>本系统不识别任何特殊 key。配置文件中所有 key 均为普通应用程序 key，
 * 系统不会占用或保留任何命名空间。</p>
 *
 * <h3>流式 API</h3>
 * <pre>{@code
 * Config config = ConfigUtil
 *     .newConfig()
 *     .load(new FileConfigSource(Paths.get("base.yaml")))  // 通用入口
 *     .loadFile("override.yaml")                            // 语法糖
 *     .loadString("key=val");                               // 语法糖
 * }</pre>
 *
 * <p>占位符 {@code {key}} 从当前已加载的配置中取值：</p>
 * <pre>{@code
 * // 假设 1.yaml 包含 { com.config.file: "2.yaml" }
 * Config config = ConfigUtil.newConfig()
 *     .loadFile("1.yaml")                   // 加载 1.yaml
 *     .loadFile("{com.config.file}")        // 从 1.yaml 取 com.config.file → "2.yaml"
 *     .loadString("url={server.host}:{server.port}/api");
 * }</pre>
 *
 * <h3>自定义来源</h3>
 * <p>实现 {@link ConfigSource} 接口并通过 {@code load(source)} 接入：</p>
 * <pre>{@code
 * Config config = ConfigUtil.newConfig()
 *     .load(new MyCustomSource(...))
 *     .loadFile("extra.yaml");
 * }</pre>
 *
 * <h3>优先级</h3>
 * <p>每个来源可通过 {@link ConfigPriority} 指定优先级。
 * 同优先级内后添加覆盖先添加；跨优先级时高优先级覆盖低优先级。</p>
 *
 * <h3>格式支持</h3>
 * <p>利用 {@code com.flora.codec} 包的解析工具（JSON/YAML/TOML/Properties）
 * 根据文件扩展名自动识别格式。</p>
 *
 * <p>核心：{@link com.flora.runtime.config.ConfigLoader}、
 * {@link com.flora.runtime.config.ConfigSource}、
 * {@link com.flora.runtime.config.Config}、
 * {@link com.flora.runtime.ConfigUtil}。</p>
 */
package com.flora.runtime.config;
