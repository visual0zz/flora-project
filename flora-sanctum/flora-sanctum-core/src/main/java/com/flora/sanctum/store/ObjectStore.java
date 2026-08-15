package com.flora.sanctum.store;

/**
 * 存储引擎接口（内部，不对外暴露；见设计 04）。
 * <p>
 * 底层为"库根文件夹里的 markdown 块集合"（见设计 04b）：每个对象一个或多个
 * base58 块。编解码由调用方注入 Codec（明文或密文）；存储层不感知密码学。
 * <p>
 * 独立文件（整文件仅一个块）默认；写入时原位替换对应 base58 串。
 */
public interface ObjectStore {

    /**
     * 读取某对象的原始字节。
     *
     * @param blockUuid 对象 UUID（块内自述）
     * @param codec     编解码器（null 视为裸明文读取）
     * @return 解码后的字节；未找到返回 {@code null}
     */
    byte[] get(java.util.UUID blockUuid, Codec codec);

    /**
     * 写入/更新某对象：重新加密（若 codec 非 null）并原位替换或新建独立文件。
     */
    void put(java.util.UUID blockUuid, byte[] data, Codec codec);

    /**
     * 删除某对象：独立文件可物理删，共享文件软删除（插入 ! 标记）。
     */
    void delete(java.util.UUID blockUuid);

    /** 列出全部对象 UUID。 */
    java.util.List<java.util.UUID> list();

    /** 扫描全部块。 */
    java.util.List<Block> scan();
}
