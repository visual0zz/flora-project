package com.flora.ai.api;

import java.util.List;

/**
 * 消息：角色 + 内容块列表。
 */
public record Message(Role role, List<ContentBlock> content) {

    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    /** 便捷构造：纯文本消息。 */
    public static Message of(Role role, String text) {
        return new Message(role, List.of(new ContentBlock.Text(text)));
    }

    /** 便捷构造：单角色纯文本列表。 */
    public static List<Message> of(Role role, String... texts) {
        return java.util.Arrays.stream(texts).map(t -> of(role, t)).toList();
    }
}
