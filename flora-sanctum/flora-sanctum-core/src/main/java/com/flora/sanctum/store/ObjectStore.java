package com.flora.sanctum.store;

/**
 * 存储引擎接口（内部，不对外暴露；见设计 04）。
 * <p>
 * 底层为"库根文件夹里的 markdown 块集合"（见设计 04b）：一文件一块，
 * {@code {第1字符}/{第2字符}/{剩余30字符}.md}（两层单字母分片），内容单行 {@code timestamp:base58}。
 * 编解码由调用方注入 Codec（明文或密文）；存储层不感知密码学。
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
     * 写入/更新某对象：重新加密（若 codec 非 null）并覆盖对应文件。
     *
     * @param blockUuid 对象 UUID（块内自述）
     * @param data      待写入字节（明文或 codec 加密后的密文，取决于 codec）
     * @param codec     编解码器（null 视为裸明文写入）
     * @param timestamp 块级时间戳（规范 ASCII 十进制字符串，落盘为 {@code timestamp:base58} 前缀，且作为 codec 的 AAD）
     * @return 写入后的块（物理位置、时间戳、字节），供上层回写内存缓存
     */
    Block put(java.util.UUID blockUuid, byte[] data, Codec codec, String timestamp);

    /**
     * 删除某对象：物理删除对应文件。
     */
    void delete(java.util.UUID blockUuid);

    /** 列出全部对象 UUID。 */
    java.util.List<java.util.UUID> list();

    /** 扫描全部块。 */
    java.util.List<Block> scan();
}
