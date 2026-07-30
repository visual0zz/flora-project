/**
 * JSONL（JSON Lines）格式的读写工具。
 * <p>将 JSONL 文件作为队列使用：{@link JsonlWriter} 追加写入，
 * {@link JsonlReader} 顺序读取，无数据时阻塞等待。</p>
 */
package com.flora.codec.jsonl;
