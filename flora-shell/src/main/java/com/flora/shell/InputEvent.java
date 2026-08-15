package com.flora.shell;

import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.java.CheckUtil;
import com.flora.root.tag.ReadOnly;

import java.util.List;

/**
 * 归一化输入：一次"命令调用"的描述，附带使用场景。
 * <p>命令调用描述只有两种既定形态，由 {@code kind()} 区分：</p>
 * <ul>
 *   <li>{@link Kind#ARGV}：argv 序列（{@code List<String>}），走 {@code ArgParser.parse}；</li>
 *   <li>{@link Kind#STRUCTURED}：结构化参数（{@link JsonObject}），走 {@code ArgParser.validate}。</li>
 * </ul>
 * <p>来源（{@link #source()}）是一次调用的使用场景（{@link UsageScenario}）。
 * 归一化在场景边界完成（argv 切成 argv 序列；Agent JSON 解析为 {@code JsonObject}），组件只接收本对象，
 * 不再二次猜测。</p>
 */
@ReadOnly
public final class InputEvent {

    /** 调用描述的形态。 */
    public enum Kind {
        /** argv 序列（{@link #argv()} 非空）。 */
        ARGV,
        /** 结构化参数（{@link #structured()} 非空）。 */
        STRUCTURED
    }

    private final UsageScenario source;
    private final String commandName;
    private final Kind kind;
    private final List<String> argv;
    private final JsonObject structured;

    private InputEvent(UsageScenario source, String commandName, Kind kind,
                       List<String> argv, JsonObject structured) {
        this.source = CheckUtil.notNull(source, "使用场景不能为空");
        this.commandName = CheckUtil.notBlank(commandName, "命令名不能为空");
        this.kind = kind;
        this.argv = argv;
        this.structured = structured;
    }

    /**
     * 创建一条 argv 形态的调用（来自一次性入口 / 文本命令分词）。
     *
     * @param source      使用场景
     * @param commandName 命令名（点分路径，如 {@code buffer.write}）
     * @param args        命令行参数（不含命令名）
     * @return InputEvent
     */
    public static InputEvent ofArgs(UsageScenario source, String commandName, String... args) {
        return new InputEvent(source, commandName, Kind.ARGV,
                args == null ? List.of() : List.of(args), null);
    }

    /**
     * 创建一条命令行入口的调用：把完整命令行参数（含命令名）切成命令名 + 剩余参数。
     * <p>来源固定为 {@link UsageScenario#CLI}。第一个元素是命令名，其余是其参数。
     * 空参数（无命令名）无法构成一次调用，抛 {@link IllegalArgumentException}；
     * 需要"无命令"报错的工具应在调用前自行判断。</p>
     *
     * @param cliArgs 完整命令行参数（第一个是命令名，不含进程名）
     * @return ARGV 形态的 InputEvent
     */
    public static InputEvent ofCliArgs(List<String> cliArgs) {
        CheckUtil.notNull(cliArgs, "命令行参数不能为空");
        if (cliArgs.isEmpty()) {
            throw new IllegalArgumentException("缺少命令名");
        }
        String commandName = cliArgs.getFirst();
        List<String> rest = List.copyOf(cliArgs.subList(1, cliArgs.size()));
        return new InputEvent(UsageScenario.CLI, commandName, Kind.ARGV, rest, null);
    }

    /**
     * 创建一条结构化调用（来自 Agent JSON 解析结果 / 快捷键绑定）。
     *
     * @param source      使用场景
     * @param commandName 命令名（点分路径）
     * @param params      参数对象（flora-root 的 JSON 正规表示）；构造时浅拷贝，之后修改原对象不影响本事件
     * @return InputEvent
     */
    public static InputEvent ofJson(UsageScenario source, String commandName, JsonObject params) {
        return new InputEvent(source, commandName, Kind.STRUCTURED, null,
                CheckUtil.notNull(params, "参数不能为空").copy());
    }

    /**
     * @return 使用场景
     */
    public UsageScenario source() {
        return source;
    }

    /**
     * @return 命令名
     */
    public String commandName() {
        return commandName;
    }

    /**
     * @return 调用描述形态
     */
    public Kind kind() {
        return kind;
    }

    /**
     * @return argv 序列；仅当 {@code kind()==ARGV} 时可用
     */
    public List<String> argv() {
        if (kind != Kind.ARGV) {
            throw new IllegalStateException("此输入不是 argv 形态");
        }
        return argv;
    }

    /**
     * @return 结构化参数（flora-root 的 JSON 正规表示）；仅当 {@code kind()==STRUCTURED} 时可用
     */
    public JsonObject structured() {
        if (kind != Kind.STRUCTURED) {
            throw new IllegalStateException("此输入不是结构化形态");
        }
        return structured;
    }

    @Override
    public String toString() {
        return "InputEvent{source=" + source + ", command='" + commandName
                + "', kind=" + kind + '}';
    }
}
