/**
 * 配置加载系统。
 *
 * <h3>设计原则：无预留命名空间</h3>
 * <p>本系统不识别任何特殊 key。配置文件中所有 key 均为普通应用程序 key，
 * 系统不会占用或保留任何命名空间。</p>
 *
 * <h3>分阶段加载（Java 编排）</h3>
 * <p>通过 {@link com.flora.runtime.config.ConfigLoader#resolve ConfigLoader.resolve}
 * 以 Java 代码编排的方式读取已加载配置中的普通 key 的值，
 * 决定下一步加载哪些配置文件。由用户代码自行决定读取哪些 key 以及如何解释其值。</p>
 * <pre>{@code
 * // 加载初始配置
 * ConfigLoader loader = new ConfigLoader();
 * loader.addSource(new FileConfigSource(Paths.get("app.yaml")));
 *
 * // 读取普通 app key "database.config" 的值，据此加载更多配置
 * ConfigMap config = loader.resolve(cfg -> {
 *     String dbPath = cfg.getString("database.config");   // 普通 key，非预留
 *     if (dbPath == null) return Collections.emptyList();
 *     return List.of(new FileConfigSource(Paths.get(dbPath)));
 * });
 * }</pre>
 *
 * <h3>优先级</h3>
 * <p>每个来源可通过 {@link ConfigPriority} 指定优先级。
 * 同优先级内后添加覆盖先添加；跨优先级时高优先级覆盖低优先级，
 * 不受添加顺序影响。</p>
 *
 * <h3>格式支持</h3>
 * <p>利用 {@code com.flora.codec} 包的解析工具（JSON/YAML/TOML/Properties）
 * 根据文件扩展名自动识别格式。</p>
 *
 * <p>核心接口：{@link com.flora.runtime.config.ConfigLoader}、
 * {@link com.flora.runtime.config.ConfigSource}、
 * {@link com.flora.runtime.config.ConfigMap}。</p>
 */
package com.flora.runtime.config;
