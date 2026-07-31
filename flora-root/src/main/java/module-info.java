/**
 * flora-root 模块定义文件。
 * <p>
 * 该模块导出核心 API 包，并声明对 {@code Converter} SPI 的使用。
 */
import com.flora.java.Converter;

module com.flora.root {
    exports com.flora.algebra;
    exports com.flora.crypto.core;
    exports com.flora.crypto.core.engine;
    exports com.flora.crypto.schemes;
    exports com.flora.entropy;
    exports com.flora.tag;
    exports com.flora.cache;
    exports com.flora.fast.container.consumer;
    exports com.flora.fast.container.map;
    exports com.flora.fast.container.tuple;
    exports com.flora.container.tuple;
    exports com.flora.codec;
    exports com.flora.java;
    exports com.flora.os;
    exports com.flora.os.secret;
    exports com.flora.os.ffi;
    exports com.flora.runtime.log;
    exports com.flora.runtime.config;
    exports com.flora.runtime.config.source;
    exports com.flora.runtime.virtual.filesys;
    exports com.flora.runtime.virtual.filesys.backend;

    exports com.flora.runtime;

    exports com.flora.codec.jsonl;

    uses Converter;

    requires static org.jetbrains.annotations;
}
