/**
 * flora-root 模块定义文件。
 * <p>
 * 零依赖核心工具库，导出大量底层 API 包，并声明对 {@code Converter} 与
 * {@code AiProvider} SPI 的使用。各导出包的定位见下方注释。
 */
import com.flora.ai.api.spi.AiProvider;
import com.flora.java.Converter;

module com.flora.root {
    // AI 能力门面
    exports com.flora.ai;
    // AI API 接口定义
    exports com.flora.ai.api;
    // AI API 默认实现
    exports com.flora.ai.api.impl;
    // AI 提供方抽象
    exports com.flora.ai.api.provider;
    // AI 客户端实现
    exports com.flora.ai.api.provider.client;
    // AI 通信协议
    exports com.flora.ai.api.provider.protocol;
    // AI 提供方 SPI
    exports com.flora.ai.api.spi;
    // AI 编排流程
    exports com.flora.ai.orchestration;
    // 代数与数学工具（如 MathUtil）
    exports com.flora.algebra;
    // 缓存抽象与实现
    exports com.flora.cache;
    // 缓存淘汰策略实现（FIFO / LFU / LRU / W-TinyLFU）
    exports com.flora.cache.eviction;
    // 缓存存储引擎与可观测装饰器（ConcurrentHashMapCache / CacheListenerAdapter）
    exports com.flora.cache.impl;
    exports com.flora.cache.interfaces;
    // 通用编解码门面
    exports com.flora.codec;
    // JSON 编解码门面(JsonParser/JsonBuilder)
    exports com.flora.codec.json;
    // JSON 值模型(JsonObject 类族)
    exports com.flora.codec.json.model;
    // JSONPath 表达式引擎
    exports com.flora.codec.json.path;
    // ASN.1 编解码
    exports com.flora.codec.asn1;
    // JSONL 编解码
    exports com.flora.codec.jsonl;
    // JSON Schema 处理
    exports com.flora.codec.jsonschema;
    exports com.flora.common;
    // 重试机制工具
    exports com.flora.concurrent.retry;
    // 通用元组类型
    exports com.flora.container.tuple;
    // 加密核心抽象与基础类型
    exports com.flora.crypto.core;
    // 加密原语桥接层
    exports com.flora.crypto.core.bridge;
    // 加密组件组合器
    exports com.flora.crypto.core.combinator;
    // 加密核心默认实现
    exports com.flora.crypto.core.impl;
    // 加密核心接口定义
    exports com.flora.crypto.core.interfaces;
    // 加密算法提供方 SPI
    exports com.flora.crypto.core.interfaces.provider;
    // 密钥对生成与管理
    exports com.flora.crypto.core.keypair;
    // 加密工作模式（如 CBC/GCM）
    exports com.flora.crypto.core.mode;
    // 填充方案
    exports com.flora.crypto.core.padding;
    // 算法参数封装
    exports com.flora.crypto.core.param;
    // 高层加密方案门面
    exports com.flora.crypto.schemes;
    // 密钥交换引擎实现
    exports com.flora.crypto.schemes.engine.kex;
    // 密钥交换方案
    exports com.flora.crypto.schemes.keyexchange;
    // 熵度量门面与聚合
    exports com.flora.entropy;
    // 压缩复杂度熵度量
    exports com.flora.entropy.compress;
    // 压缩熵引擎实现
    exports com.flora.entropy.compress.engine;
    // 基于 zlib 的压缩熵
    exports com.flora.entropy.compress.zlib;
    // 熵度量汇总与归一化（EntropyEstimator）
    exports com.flora.entropy.mesure;
    // 熵度量算法引擎
    exports com.flora.entropy.mesure.engine;
    // 高性能容器消费者
    exports com.flora.fast.container.consumer;
    // 高性能映射容器
    exports com.flora.fast.container.map;
    // 高性能元组容器
    exports com.flora.fast.container.tuple;
    // Java 基础工具类（Converter 与各 *Util）
    exports com.flora.java;
    // JSON Schema 数据生成 mock
    exports com.flora.mock.jsonschema;
    // 正则字符串生成 mock
    exports com.flora.mock.regex;
    // 正则自动机实现
    exports com.flora.mock.regex.automaton;
    // 操作系统与路径相关工具
    exports com.flora.os;
    // 基于 JDK FFM 的本地动态库调用封装
    exports com.flora.os.natives.ffm;
    // 运行时配置加载
    exports com.flora.runtime.config;
    // 配置加载实现
    exports com.flora.runtime.config.impl;
    exports com.flora.runtime.config.interfaces;
    exports com.flora.runtime.config.source;
    // 运行时日志门面
    exports com.flora.runtime.log;
    // 日志实现 SPI
    exports com.flora.runtime.log.spi;
    // 虚拟文件系统抽象
    exports com.flora.runtime.virtual.filesys;
    // 虚拟文件系统后端实现
    exports com.flora.runtime.virtual.filesys.backend;
    // 括号匹配分析
    exports com.flora.syntax.bracket;
    // 语法解析公共类型
    exports com.flora.syntax.common;
    // 语法定义模型
    exports com.flora.syntax.common.definition;
    // 语法解析公共异常
    exports com.flora.syntax.common.exceptions;
    // 表达式解析
    exports com.flora.syntax.expr;
    // PEG 语法解析器
    exports com.flora.syntax.peg;
    // 语义/目的标注注解（如 ModuleEntry）
    exports com.flora.tag;

    uses Converter;
    uses AiProvider;

    requires java.net.http;
    requires static org.jetbrains.annotations;
}
