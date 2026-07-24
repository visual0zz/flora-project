/**
 * flora-root 模块定义文件。
 * <p>
 * 该模块导出核心 API 包，并声明对 {@code Converter} SPI 的使用。
 */
import com.flora.java.Converter;

module com.flora.root {
    exports com.flora.algebra;
    exports com.flora.crypto;
    exports com.flora.entropy;
    exports com.flora.tag;
    exports com.flora.cache;
    exports com.flora.fast.container.consumer;
    exports com.flora.fast.container.map;
    exports com.flora.fast.container.tuple;
    exports com.flora.container.tuple;
    exports com.flora.container;
    exports com.flora.codec;
    exports com.flora.data;
    exports com.flora.java;
    exports com.flora.log;

    uses Converter;
}
