package com.flora.sanctum.store;

/**
 * 编解码接口（内部，不对外暴露；见设计 04）。
 * <p>
 * 每次读写由调用方注入；codec 为 null 视为裸明文读写（无信封、无验证）。
 */
public interface Codec {

    /** 编码（加密或恒等），输入解码后的字节，输出落盘前的字节。timestamp 为块级时间戳（规范 ASCII 十进制字符串，落盘前缀原文，进入 AAD）。 */
    byte[] encode(byte[] data, String timestamp);

    /** 解码（解密或恒等），输入落盘字节，输出解码后的字节。timestamp 为块级时间戳（规范 ASCII 十进制字符串，落盘前缀原文，重建 AAD）。 */
    byte[] decode(byte[] data, String timestamp);
}
