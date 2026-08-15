package com.flora.shell;

import com.flora.root.codec.json.model.JsonValue;
import com.flora.root.java.CheckUtil;
import com.flora.root.tag.ReadOnly;

import java.util.Objects;

/**
 * 命令执行结果。
 * <p>只表达执行的状态与结构化数据；一切文本信息（成功或报错）都是描述性质的，
 * 统一通过日志记录，不进本对象。命令在 {@code execute} 中用 {@code Invocation.log()}
 * 记录文本，用 {@link #data(Object)} 返回机器可读的结构化结果。</p>
 * <p>状态（{@link Status}）区分三类结果：命令成功、命令自身的错误（如参数/业务错误）、
 * 框架/系统错误（如来源不符、未知命令、执行异常）。进程退出值由调用方按状态判定。</p>
 */
@ReadOnly
public final class CommandResult {

    /** 结果状态。 */
    public enum Status {
        /** 命令执行成功。 */
        SUCCESS,
        /** 命令自身的错误（参数错误、业务错误等）。 */
        COMMAND_ERROR,
        /** 框架 / 系统错误（来源不符、未知命令、转发超限、执行异常等）。 */
        SYSTEM_ERROR
    }

    private final Status status;
    private final JsonValue data;

    private CommandResult(Status status, JsonValue data) {
        this.status = CheckUtil.notNull(status, "状态不能为空");
        this.data = data;
    }

    /**
     * @return 成功结果，状态 SUCCESS、无数据
     */
    public static CommandResult success() {
        return new CommandResult(Status.SUCCESS, null);
    }

    /**
     * @return 命令错误结果，状态 COMMAND_ERROR、无数据
     */
    public static CommandResult failure() {
        return new CommandResult(Status.COMMAND_ERROR, null);
    }

    /**
     * @return 命令自身错误结果（报错详情由调用方记日志）
     */
    public static CommandResult commandError() {
        return failure();
    }

    /**
     * @return 框架 / 系统错误结果（报错详情由调用方记日志）
     */
    public static CommandResult systemError() {
        return new CommandResult(Status.SYSTEM_ERROR, null);
    }

    /**
     * @param data 结构化返回数据（JSON 值，机器可读）
     * @return 成功且携带结构化数据的结果
     */
    public static CommandResult data(JsonValue data) {
        return new CommandResult(Status.SUCCESS, CheckUtil.notNull(data, "结果数据不能为空"));
    }

    /**
     * @return 结果状态
     */
    public Status status() {
        return status;
    }

    /**
     * @return 结构化 JSON 数据；可能为 {@code null}
     */
    public JsonValue data() {
        return data;
    }

    @Override
    public String toString() {
        return "CommandResult{status=" + status + ", data=" + Objects.toString(data) + '}';
    }
}
