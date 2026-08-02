package com.flora.ai.api.impl;

/**
 * 内容块：消息内容的一部分，支持多模态（文本/图片/音频/文件）。
 * <p>{@code dataUrl} 为 base64 数据或可访问 URL，配合 {@code mediaType} 描述格式。</p>
 */
public sealed interface ContentBlock {

    /** 文本块。 */
    record Text(String text) implements ContentBlock {
    }

    /** 图片块。 */
    record Image(String dataUrl, String mediaType) implements ContentBlock {
    }

    /** 音频块。 */
    record Audio(String dataUrl, String mediaType) implements ContentBlock {
    }

    /** 文件块（PDF/文档等）。 */
    record File(String dataUrl, String mediaType) implements ContentBlock {
    }
}
