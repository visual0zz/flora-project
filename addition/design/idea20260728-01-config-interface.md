# config 包接口草图（Spring 风格：单 key 树 + 前缀作用域 + 有序回退 + 零保留命名空间）

日期：2026-07-28
状态：草图（已确定方向：零保留命名空间，未实现）

## 背景与目标

`com.flora.runtime.config` 包准备承载通用配置加载系统。设计借鉴 Spring Boot 的成熟模型，
但定位不同：Spring 是「有主张的全家桶框架」，会保留 `spring.*` 约定键；`flora-root` 是
**零依赖工具库**，被上层集成，不应替使用者定规矩。

本设计的一条硬约束：**框架默认零保留命名空间**。框架不硬编码、不扫描任何形如
`flora.*` 的保留键。所有「特殊含义」（profile 激活、来源引导链）要么由 API 显式驱动，
要么由应用自己提供键名（opt-in）。这样彻底消除键名冲突与「保留键守卫」负担。

同时刻意**不引入包作用域、不做调用方包自动定位**，以避免「包链 + key 链」双链认知混乱：

- 配置只有**一条点号 key 树**（如 `db.pool.maxSize`），点号只有一种含义。
- 作用域 = **key 前缀**（`db.pool`），由调用者显式声明，不按调用方所在包推断。
- 回退 = **有序来源列表的优先级覆盖**，单一规则，不是包树向上走。

## 设计原则

1. 单一 key 命名空间（dotted keys），无包链。
2. 前缀作用域，显式绑定（`getConfig(prefix)` / `bind(prefix, type)`）。
3. 来源有序（`order` 越大优先级越高），`getProperty` 高优先级覆盖低优先级，首个非空即返回。
4. 不可变视图：`build()` 返回不可变 `Config` 快照，线程安全。
5. 零依赖：默认提供 `PropertiesConfigSource`（基于 `java.util.Properties`）；YAML 来源后续作为可选扩展。
6. **零保留命名空间**：框架不认任何固定键名；profile / 引导链由 API 或应用自选 opt-in 键名驱动。

## 接口草图

```java
package com.flora.runtime.config;

import java.util.Set;

/** 一个具名、有序、可贴 profile 标签的配置来源（类比 Spring PropertySource）。 */
public interface ConfigSource {
    String getName();
    String getProperty(String key);        // 找不到返回 null
    boolean containsKey(String key);
    Set<String> propertyNames();
    default int getOrder() { return 0; }                // 越大优先级越高
    default Set<String> getProfiles() { return Set.of(); } // 空 = 始终生效；非空 = 仅当 profile 激活
}

/** 统一门面（类比 Spring Environment）。单一 key 树，无包链，无保留键。 */
public interface Config {
    String getProperty(String key);
    String getProperty(String key, String def);
    int getIntProperty(String key, int def);
    long getLongProperty(String key, long def);
    boolean getBooleanProperty(String key, boolean def);
    boolean containsProperty(String key);

    /** 前缀子树导航：返回 prefix 下的子视图，key 自动加 prefix + "."。 */
    Config getConfig(String prefix);

    /** 可选：把 prefix 子树绑定到 Java Bean（类型安全，反射填充）。 */
    <T> T bind(String prefix, Class<T> type);
}

/**
 * 加载/注册（类比 Spring ConfigData + PropertySources）。
 * 框架自身不扫描任何保留键；policy 由 API 或 opt-in 键名给出。
 */
public final class ConfigLoader {
    public static ConfigLoader create();

    // —— 来源注册 ——
    public ConfigLoader addSource(ConfigSource source);   // 通用扩展点（远程/DB 自定义实现走这里）
    public ConfigLoader load(Path file);                  // 默认 order
    public ConfigLoader load(Path file, int order);
    public ConfigLoader loadClasspath(String resource);  // jar 内打包资源，默认 order
    public ConfigLoader loadClasspath(String resource, int order);

    // —— profile（无保留键：纯 API 或 opt-in 键名）——
    public ConfigLoader activeProfiles(String... profiles);  // API 显式激活
    public ConfigLoader profilesKey(String key);            // opt-in：用应用自选键名驱动 profile
                                                          //   不调用则框架完全不认任何 profile 键

    // —— 引导链（无保留键：靠 preview() + 应用编排）——
    public Config preview();                              // 用「当前已加来源」拼临时 Config，不终结 loader
    public Config build();                                // 组装不可变 Config
}

/** 具体来源：基于 java.util.Properties，零依赖。 */
public final class PropertiesConfigSource implements ConfigSource { /* ... */ }

/** 可选扩展：YAML 来源（需解析器，后续再加，支持多文档分区 on-profile）。 */
// public final class YamlConfigSource implements ConfigSource { /* ... */ }

/** 缺失必需配置项时抛出。 */
public class ConfigException extends RuntimeException { /* ... */ }
```

## 解析规则

- `getProperty(key)`：按 `order` 降序遍历所有 **当前已生效** 的来源，返回首个非 null；全部为 null 返回 `def`（或 null）。
- `getConfig(prefix)`：子视图的 `getProperty(k)` 委托给父视图 `getProperty(prefix + "." + k)`，仍走同一套有序回退。
- **生效（active）含义**：来源分两类——`getProfiles()` 为空的「始终生效」来源，与 profile 在
  `activeProfiles(...)` 集合内的「profile 专属」来源。profile 标签是来源构造时的**方法级**
  属性（`addSource` / `load` 时由应用标注），与任何 key 无关。
- `bind(prefix, type)`：反射构造 `type` 实例，对 `type` 的字段/setter 按 `prefix.<fieldName>`
  （relaxed：支持 `maxSize`/`max-size`/`MAX_SIZE`）查找并转换类型（int/long/boolean/String/enum）。

## 零保留命名空间：三种 policy 驱动方式

框架不内置 `flora.profiles.active` / `flora.bootstrap.*` 之类的保留键。特殊行为由应用选择其一：

### 方式 A — 纯 API（推荐默认，最干净）
profile 由启动参数 / 环境变量直接给出；引导链由应用用 `preview()` 读值后调 `load` 编排。
框架视角里所有 key 都是普通 key，无任何特殊名。

```java
ConfigLoader loader = ConfigLoader.create();
loader.loadClasspath("application-internal.properties", 0);   // jar 内，兜底 order

Config boot = loader.preview();                               // 框架不知这是"引导键"，只是普通读值
String local = boot.getProperty("app.config.localPath");      // 键名由应用自定
loader.load(Paths.get(local), 100);

boot = loader.preview();
String remote = boot.getProperty("app.config.remoteUrl");     // 键名由应用自定
loader.load(classpathOrRemoteSource, 200);                    // 远程/DB：自定义 ConfigSource 经 addSource 加入

loader.activeProfiles(System.getenv("APP_PROFILE"));          // profile 由 API 给出
Config config = loader.build();
```

要点：所谓「引导链」「profile 键」只存在于应用代码的*意图*里，框架不区分普通键与引导键。

### 方式 B — opt-in 键名（想要文件决定 profile 时）
若应用确实想「配置文件里的某项决定 profile」，用 `profilesKey` 注册**应用自选**键名，
框架不会内置任何 `flora.*` 保留键：

```java
ConfigLoader.create()
    .profilesKey("app.config.activeProfile")   // 应用自选键名，非框架保留
    .loadClasspath("application-internal.properties")
    .build();
```

不调用 `profilesKey(...)` 时，框架完全不认任何 profile 键 —— 零保留命名空间常态成立。

### 方式 C — 后续 opt-in 便利（不在草图强制范围）
`importKey(String key)`：注册应用自选键名，loader 在 `build()` 内读该键并递归跟随来源
（类比 Spring `spring.config.import`）。属于可选封装，核心仍可用方式 A 的 `preview()` 等价实现。

## profile 是什么（澄清）

profile 是**激活标签**，不是文件名后缀：
- 它作用在三处：profile 专属来源过滤、Bean 条件（上层框架）、以及（可选）从某键读出激活。
- 在我们的模型里，profile 仅体现为 `ConfigSource.getProfiles()` 的方法级标签 + `activeProfiles(...)` 集合。
- 文件名 `application-{profile}.*` 只是「把整个文件关联到某 profile」的一种约定，由应用在建源时标注；
  框架不解析文件名后缀，除非应用选择这么约定。
- 一个文件内分区属于不同 profile 是 YAML 多文档（`---` + `on-profile`）的专属能力，properties 不支持；
  我们整文件级标签方案对两种格式都成立。

## 引导链：发现顺序 ≠ 值优先级（重要）

jar 内 → 本地 → 远程 这类引导链涉及两个独立维度，切勿混为一谈：

- **发现顺序**（串行）：先 jar 内 → 读 `localPath` → 加载本地 → 读 `remoteUrl` → 加载远程。
  由 `preview()` + 应用编排驱动，或（opt-in）`importKey` 驱动。
- **值优先级**（由 `order` 决定，与「何时加入」无关）：通常 jar 内 `order=0`（兜底）、
  本地居中、远程 `order=200`（覆盖）。即**最后发现、却优先级最高**。

框架视角：发现阶段读「当前已加来源」的全部 key（不分 order）；值解析阶段按 order 降序回退。

## 与日志加载器的关系

日志加载器作为**消费者**使用本系统：读取配置时走 key 前缀 `logger.<pkg>`
（如 `logger.com.flora.db.level`），**不**依赖调用方包自动定位。日志前缀键名由日志模块自定，
不属于框架保留命名空间。这样日志配置与任意业务配置共用同一套「单 key 树 + 前缀作用域 +
有序回退」，无双链混乱、无保留键冲突。

## 代价与收益对照

| 维度 | 有保留键（Spring 风） | 零保留键（本设计） |
|---|---|---|
| 框架体积/约定 | 重，需记 `flora.*` 语义 | 轻，无任何约定 |
| 键名冲突/守卫 | 需文档+告警防误用 `flora.*` | 不存在，所有 key 归应用 |
| 引导链/自动 profile | `build()` 内自动完成 | 应用用 `preview()` 几行胶水编排（或 opt-in 键名） |
| 灵活性 | 受限（必须按约定） | 高（键名/策略全自定义） |

对一个零依赖的 `flora-root` 工具库，后者更合适：不替使用者定规矩，只提供能力。

## 后续扩展（不在本次草图强制范围）

- `YamlConfigSource`（需引入或自写最小 YAML 解析；支持多文档 on-profile 分区）。
- `bind` 类型绑定器（反射）。
- `activeProfiles` 与 profile 特定来源加载（方法级 `getProfiles()` 已就位）。
- 远程/DB 来源：`HttpConfigSource` / `DbConfigSource` 实现 `ConfigSource`，经 `addSource` 加入；
  可选 `load(URI, order)` + scheme→工厂映射作为便利封装。
- opt-in `importKey` 自动引导（方式 C）。
- `optional:` 语义（加载失败不致命）作为 opt-in 装饰，而非保留前缀。
