package com.flora.shell;

import com.flora.root.java.CheckUtil;

import java.util.Objects;

/**
 * 输入来源身份标识（渠道）。
 * <p>标识一次 {@link InputEvent} 来自哪个渠道（argv、Agent、未来的 TUI/微信等）。
 * 用类型而非裸字符串，保证来源过滤的类型安全，避免跨模块拼写错误。</p>
 * <p>本类提供 {@code ARGV}（一次性入口）与 {@code AGENT}（AI Agent 结构化调用）
 * 两个内建来源；未来多渠道落地时，宿主可创建更多 {@code ChannelId} 实例。</p>
 */
public final class ChannelId {

    /** 一次性命令行入口（argv）来源。 */
    public static final ChannelId ARGV = new ChannelId("argv");

    /** AI Agent 结构化调用来源。 */
    public static final ChannelId AGENT = new ChannelId("agent");

    private final String id;

    /**
     * @param id 渠道标识，不允许为空白
     */
    public ChannelId(String id) {
        CheckUtil.notBlank(id, "渠道标识不能为空");
        this.id = id;
    }

    /**
     * @return 渠道标识字符串
     */
    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChannelId that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
