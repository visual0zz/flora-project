/**
 * flora-root 模块定义文件。
 * <p>
 * 零依赖核心工具库，导出大量底层 API 包，并声明对 {@code Converter} 与
 * {@code AiProvider} SPI 的使用。各导出包的定位见下方注释。
 */
import com.flora.root.ai.api.spi.AiProvider;
import com.flora.root.java.Converter;

module com.flora.root {
    // AI 能力门面
    exports com.flora.root.ai;
    // AI API 接口定义
    exports com.flora.root.ai.api;
    // AI API 默认实现
    exports com.flora.root.ai.api.impl;
    // AI 提供方抽象
    exports com.flora.root.ai.api.provider;
    // AI 客户端实现
    exports com.flora.root.ai.api.provider.client;
    // AI 通信协议
    exports com.flora.root.ai.api.provider.protocol;
    // AI 提供方 SPI
    exports com.flora.root.ai.api.spi;
    // AI 编排流程
    exports com.flora.root.ai.orchestration;
    // 代数与数学工具（如 MathUtil）
    exports com.flora.root.algebra;
    // 缓存抽象与实现
    exports com.flora.root.cache;
    // 缓存淘汰策略实现（FIFO / LFU / LRU / W-TinyLFU）
    exports com.flora.root.cache.eviction;
    // 缓存存储引擎与可观测装饰器（ConcurrentHashMapCache / CacheListenerAdapter）
    exports com.flora.root.cache.impl;
    exports com.flora.root.cache.interfaces;
    // 通用编解码门面
    exports com.flora.root.codec;
    // JSON 编解码门面(JsonParser/JsonBuilder)
    exports com.flora.root.codec.json;
    // JSON 值模型(JsonObject 类族)
    exports com.flora.root.codec.json.model;
    // JSONPath 表达式引擎
    exports com.flora.root.codec.json.path;
    // ASN.1 编解码
    exports com.flora.root.codec.asn1;
    // JSONL 编解码
    exports com.flora.root.codec.jsonl;
    // JSON Schema 处理
    exports com.flora.root.codec.jsonschema;
    exports com.flora.root.common;
    // 通用算法抽象与注册中心（Algorithm/AlgorithmFactory/AbstractAlgorithmFactoryRegister）
    exports com.flora.root.common.register;
    // 英文单词列表与 Diceware 口令生成（WordList/PassphraseGenerator）
    exports com.flora.root.common.words;
    // 重试机制工具
    exports com.flora.root.concurrent.retry;
    // 通用元组类型
    exports com.flora.root.container.tuple;
    // N 元 Sum Type 容器 Variant（任意多个类型任取其一）
    exports com.flora.root.container;
    exports com.flora.root.entropy;
    // 压缩复杂度熵度量
    exports com.flora.root.entropy.compress;
    // 压缩熵引擎实现
    exports com.flora.root.entropy.compress.engine;
    // 基于 zlib 的压缩熵
    exports com.flora.root.entropy.compress.zlib;
    // 熵度量汇总与归一化（EntropyEstimator）
    exports com.flora.root.entropy.mesure;
    // 熵度量算法引擎
    exports com.flora.root.entropy.mesure.engine;
    // 高性能容器消费者
    exports com.flora.root.fast.container.consumer;
    // 高性能映射容器
    exports com.flora.root.fast.container.map;
    // 高性能元组容器
    exports com.flora.root.fast.container.tuple;
    // 图形与噪声工具（PaperNoise 纸纤维噪声）
    exports com.flora.root.graphics.noise;
    // Java 基础工具类（Converter 与各 *Util）
    exports com.flora.root.java;
    // JSON Schema 数据生成 mock
    exports com.flora.root.mock.jsonschema;
    // 正则字符串生成 mock
    exports com.flora.root.mock.regex;
    // 正则自动机实现
    exports com.flora.root.mock.regex.automaton;
    // 操作系统与路径相关工具
    exports com.flora.root.os;
    // 跨平台终端 ANSI 颜色与样式工具
    exports com.flora.root.os.shell.color;
    // 基于 JDK FFM 的本地动态库调用封装
    exports com.flora.root.os.natives.ffm;
    // 运行时配置加载
    exports com.flora.root.runtime.config;
    // 配置加载实现
    exports com.flora.root.runtime.config.impl;
    exports com.flora.root.runtime.config.interfaces;
    exports com.flora.root.runtime.config.source;
    // 运行时日志门面
    exports com.flora.root.runtime.log;
    // 日志实现 SPI
    exports com.flora.root.runtime.log.spi;
    // 虚拟文件系统抽象
    exports com.flora.root.runtime.virtual.filesys;
    // 虚拟文件系统后端实现
    exports com.flora.root.runtime.virtual.filesys.backend;
    // 语义/目的标注注解（如 ModuleEntry）
    exports com.flora.root.tag;

    uses Converter;
    uses AiProvider;

    requires java.net.http;
    requires static org.jetbrains.annotations;
}
