package com.flora.shell;

import com.flora.root.java.CheckUtil;
import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;
import com.flora.root.tag.ThreadFragile;
import com.flora.shell.builtin.AliasCommand;
import com.flora.shell.builtin.HelpCommand;
import com.flora.shell.spec.ArgParser;
import com.flora.shell.spec.ParsedArgs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 指令组件：干净的路由与执行单元。
 * <p>负责命令注册、别名、串行分派与结果回调；零状态、无 UI、不拥有输入源、不管生命周期。
 * 构造时绑定一个使用场景（{@link UsageScenario}），只接受自报支持该场景的命令注册。
 * 批量与 Agent 接入各自以对应场景建立实例。构造时自动注册内置 {@code help} 与
 * {@code alias} 指令。</p>
 * <p>分派规则：{@link #submit} 将输入加入串行队列（多渠道并发提交安全），校验调用来源
 * 与实例场景一致后，查找命令 + {@link ArgParser} 校验 → 构造 {@link Invocation} → 执行。
 * 每次执行完毕，把 {@link InputEvent} 与 {@link CommandResult} 交给所有已注册的 sink
 * （见 {@link #newSink}）。内部错误与命令日志经 {@link Logger} 记录。</p>
 * <p>转发与别名：命令执行中可经 {@link Invocation#forward} 重入分派（{@link Dispatcher}）；
 * 分派未命中真实命令时按别名解析转发。转发与别名共享同一分派管线与递归深度上限，防止
 * 别名环导致的无限递归。</p>
 * <p>同名冲突裁决：用户命令与内置指令冲突时按 {@code priority()} 高者胜出（内置指令为负，
 * 用户命令可覆写内置指令）；用户命令之间同名冲突直接抛异常（视为 bug），不裁决。</p>
 */
@ThreadFragile("串行分派依赖 ReentrantLock 与 depth 字段，sink 回调可能在锁内重入")
public final class CommandService implements Dispatcher {

    /** 转发 / 别名解析的最大递归深度，超过则视为存在环并拒绝。 */
    static final int MAX_FORWARD_DEPTH = 16;

    private final UsageScenario scenario;
    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final Map<String, ArgParser> parsers = new LinkedHashMap<>();
    private final Map<String, Alias> aliases = new LinkedHashMap<>();
    private final List<CommandSink> sinks = new CopyOnWriteArrayList<>();
    private final ReentrantLock dispatchLock = new ReentrantLock();
    private final Logger logger;
    private int depth;

    /**
     * 创建绑定指定使用场景的指令组件，并注册内置 {@code help} 与 {@code alias} 指令。
     *
     * @param scenario 本实例限定的使用场景，只接受支持该场景的命令注册
     */
    public CommandService(UsageScenario scenario) {
        this(scenario, LoggerFactory.getLogger(CommandService.class));
    }

    /**
     * 创建绑定指定使用场景的指令组件，使用指定的日志器（内部错误记录与命令日志）。
     *
     * @param scenario 本实例限定的使用场景
     * @param logger   用于内部错误记录与命令级日志的底层日志器
     */
    public CommandService(UsageScenario scenario, Logger logger) {
        this.scenario = CheckUtil.notNull(scenario, "使用场景不能为空");
        this.logger = CheckUtil.notNull(logger, "日志器不能为空");
        register(new HelpCommand(this));
        register(new AliasCommand(this));
    }

    /**
     * @return 本实例绑定的使用场景
     */
    public UsageScenario scenario() {
        return scenario;
    }

    /**
     * 注册一个命令；同名冲突按 {@link Command#priority()} 裁决。
     * <p>命令必须在其 {@link Command#usageScenarios()} 中声明支持本实例的场景，否则拒绝注册。</p>
     *
     * @param command 命令
     * @throws IllegalArgumentException 命令不支持本实例场景，或用户命令之间同名冲突（视为 bug）
     */
    public void register(Command command) {
        CheckUtil.notNull(command, "命令不能为空");
        if (!command.usageScenarios().contains(scenario)) {
            throw new IllegalArgumentException("命令 " + command.name() + " 不支持使用场景 " + scenario
                    + "（声明支持: " + command.usageScenarios() + "）");
        }
        Command existing = commands.get(command.name());
        if (existing == null) {
            commands.put(command.name(), command);
            parsers.put(command.name(), new ArgParser(command.args()));
            return;
        }
        if (existing.getClass() == command.getClass()) {
            return; // 同类的重复注册，幂等忽略
        }
        if (command.priority() > existing.priority()) {
            commands.put(command.name(), command);
            parsers.put(command.name(), new ArgParser(command.args()));
            return;
        }
        if (command.priority() < existing.priority()) {
            return; // 新命令优先级更低，保留旧命令
        }
        throw new IllegalArgumentException("命令名冲突（优先级相同）: " + command.name()
                + " 已由 " + existing.getClass().getSimpleName() + " 注册，无法与 "
                + command.getClass().getSimpleName() + " 区分");
    }

    /**
     * 通过 SPI 自动发现并注册全部 {@link Command} 提供方。
     * <p>由各工具模块用 {@code provides Command} 声明，本方法经 {@link ServiceLoader} 加载。</p>
     */
    public void registerBySpi() {
        for (Command command : ServiceLoader.load(Command.class)) {
            register(command);
        }
    }

    /**
     * 注册（或更新）一个别名：{@code name} 被调用时转发到 {@code target}，参数为
     * {@code prefixArgs} 追加本次调用的剩余参数。
     *
     * @param name       别名
     * @param target     目标命令名
     * @param prefixArgs 附加在本次参数之前的目标参数
     */
    public void setAlias(String name, String target, List<String> prefixArgs) {
        CheckUtil.notBlank(name, "别名不能为空");
        CheckUtil.notBlank(target, "目标命令名不能为空");
        aliases.put(name, new Alias(target, List.copyOf(prefixArgs)));
    }

    /**
     * 移除一个别名。
     *
     * @param name 别名
     */
    public void removeAlias(String name) {
        aliases.remove(name);
    }

    /**
     * @return 全部别名的不可变快照（name → 别名）
     */
    public Map<String, Alias> aliases() {
        return new LinkedHashMap<>(aliases);
    }

    /**
     * @param name 命令名
     * @return 指定命令；不存在返回 {@code null}
     */
    public Command find(String name) {
        return commands.get(name);
    }

    /**
     * @return 已注册命令的不可变快照（按注册顺序）
     */
    public List<Command> commands() {
        return new ArrayList<>(commands.values());
    }

    /**
     * 注册一个命令执行观察 sink。
     * <p>之后每次命令执行完毕，都会把本次的 {@link InputEvent} 与 {@link CommandResult}
     * 交给 {@code observer}；调用返回的 {@link CommandSink#close()} 可移除该 sink。</p>
     *
     * @param observer 观察者，形如 {@code (event, result) -> ...}
     * @return 可关闭的 sink 句柄
     */
    public CommandSink newSink(CommandObserver observer) {
        CheckUtil.notNull(observer, "观察者不能为空");
        CommandSink[] holder = new CommandSink[1];
        CommandSink sink = new CommandSink(observer, () -> sinks.remove(holder[0]));
        holder[0] = sink;
        sinks.add(sink);
        return sink;
    }

    @Override
    public CommandResult submit(InputEvent event) {
        CheckUtil.notNull(event, "输入事件不能为空");
        if (event.source() != scenario) {
            logger.error("调用来源 {} 与组件场景 {} 不一致", event.source(), scenario);
            CommandResult result = CommandResult.systemError();
            notify(event, result);
            return result;
        }
        dispatchLock.lock();
        try {
            CommandResult result = dispatch(event);
            notify(event, result);
            return result;
        } finally {
            dispatchLock.unlock();
        }
    }

    /**
     * 统一的串行分派实现：转发 / 别名解析、参数解析、执行都在此完成。
     * <p>用字段 {@code depth} 追踪递归深度（同一锁内同线程，串行安全），超限视为别名环。</p>
     */
    private CommandResult dispatch(InputEvent event) {
        if (++depth > MAX_FORWARD_DEPTH) {
            depth--;
            logger.error("转发深度超出限制（可能存在别名环）: {}", event.commandName());
            return CommandResult.systemError();
        }
        try {
            Command command = find(event.commandName());
            if (command == null) {
                return dispatchAlias(event);
            }
            return execute(command, event);
        } finally {
            depth--;
        }
    }

    /** 真实命令未命中时，按别名解析转发；无别名则报未知命令。 */
    private CommandResult dispatchAlias(InputEvent event) {
        Alias alias = aliases.get(event.commandName());
        if (alias == null) {
            logger.error("未知命令: {}（输入 help 查看可用命令）", event.commandName());
            return CommandResult.systemError();
        }
        List<String> argv = new ArrayList<>(alias.prefixArgs());
        if (event.kind() == InputEvent.Kind.ARGV) {
            argv.addAll(event.argv());
        }
        return dispatch(InputEvent.ofArgv(event.source(), alias.target(), argv));
    }

    /** 执行单个命令（含参数解析、执行）。结果由 submit 统一扇出。 */
    private CommandResult execute(Command command, InputEvent event) {
        ParsedArgs parsed;
        try {
            parsed = parse(command, event);
        } catch (IllegalArgumentException e) {
            logger.error("命令 {} 参数解析失败: {}", command.name(), e.getMessage());
            return CommandResult.commandError();
        }
        Invocation inv = new Invocation(command, parsed, event.source(), this,
                new CommandLogger(logger, command.name()));
        try {
            return command.execute(inv);
        } catch (Exception e) {
            logger.error("命令 {} 执行失败", command.name(), e);
            return CommandResult.systemError();
        }
    }

    /**
     * 执行完毕后，把 (InputEvent, CommandResult) 交给所有已注册的 sink（结构化观察者）。
     * 单个 sink 抛异常不影响其他 sink 与主流程，仅记录日志。
     */
    private void notify(InputEvent event, CommandResult result) {
        for (CommandSink sink : sinks) {
            try {
                sink.observer().onExecuted(event, result);
            } catch (Exception e) {
                logger.error("命令执行通知 sink 失败: " + e.getMessage(), e);
            }
        }
    }

    private ParsedArgs parse(Command command, InputEvent event) {
        ArgParser parser = parsers.get(command.name());
        if (parser == null) {
            parser = new ArgParser(command.args());
            parsers.put(command.name(), parser);
        }
        if (event.kind() == InputEvent.Kind.ARGV) {
            return parser.parse(event.argv());
        }
        return parser.validate(event.structured());
    }

    /** 别名值对象。 */
    public static final class Alias {
        private final String target;
        private final List<String> prefixArgs;

        Alias(String target, List<String> prefixArgs) {
            this.target = target;
            this.prefixArgs = prefixArgs;
        }

        /**
         * @return 目标命令名
         */
        public String target() {
            return target;
        }

        /**
         * @return 附加在本次参数之前的目标参数
         */
        public List<String> prefixArgs() {
            return prefixArgs;
        }

        @Override
        public String toString() {
            return "alias -> " + target + (prefixArgs.isEmpty() ? "" : " " + String.join(" ", prefixArgs));
        }
    }
}
