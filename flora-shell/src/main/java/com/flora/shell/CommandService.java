package com.flora.shell;

import com.flora.root.java.CheckUtil;
import com.flora.shell.builtin.HelpCommand;
import com.flora.shell.help.HelpRenderer;
import com.flora.shell.output.OutputMultiplexer;
import com.flora.shell.spec.ArgParser;
import com.flora.shell.spec.ParsedArgs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 指令组件：干净的路由与执行单元。
 * <p>负责命令注册、串行分派与输出扇出；零状态、无 UI、不拥有输入源、不管生命周期。
 * 批量与 Agent 接入只需用它。构造时自动注册内置 {@code help} 指令。</p>
 * <p>分派规则：{@link #submit} 将输入加入串行队列（多渠道并发提交安全），查找命令 +
 * {@link ArgParser} 校验 → 构造 {@link Invocation} → 执行，结果经 {@link OutputMultiplexer}
 * 扇出到所有已挂载的输出汇。</p>
 * <p>同名冲突裁决：用户命令与内置指令冲突时按 {@code priority()} 高者胜出（内置指令为负，
 * 用户命令可覆写内置指令）；用户命令之间同名冲突直接抛异常（视为 bug），不裁决。</p>
 */
public final class CommandService {

    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final Map<String, ArgParser> parsers = new LinkedHashMap<>();
    private final OutputMultiplexer out = new OutputMultiplexer();
    private final HelpRenderer help = new HelpRenderer();
    private final ReentrantLock dispatchLock = new ReentrantLock();

    /**
     * 创建指令组件，并注册内置 {@code help} 指令。
     */
    public CommandService() {
        register(new HelpCommand(this, help));
    }

    /**
     * 注册一个命令；同名冲突按 {@link Command#priority()} 裁决。
     *
     * @param command 命令
     * @throws IllegalArgumentException 用户命令之间同名冲突（视为 bug）
     */
    public void register(Command command) {
        CheckUtil.notNull(command, "命令不能为空");
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
     * 挂载一个输出汇；之后的命令输出同时到达该汇（并继续到达 stdout/stderr）。
     *
     * @param sink 输出汇
     */
    public void attach(com.flora.shell.output.OutputSink sink) {
        out.attach(sink);
    }

    /**
     * 卸载一个输出汇。
     *
     * @param sink 输出汇
     */
    public void detach(com.flora.shell.output.OutputSink sink) {
        out.detach(sink);
    }

    /**
     * 提交一次调用并串行执行。
     *
     * @param event 归一化输入
     * @param state 调用方领域状态（可传 {@code null}）
     * @return 执行结果
     */
    public CommandResult submit(InputEvent event, Object state) {
        CheckUtil.notNull(event, "输入事件不能为空");
        dispatchLock.lock();
        try {
            Command command = find(event.commandName());
            if (command == null) {
                out.error("未知命令: " + event.commandName() + "（输入 help 查看可用命令）");
                return CommandResult.exit(CommandResult.FAILURE);
            }
            // 来源限制检查
            if (command instanceof Command.SourceRestricted restricted) {
                var allowed = restricted.allowedSources();
                if (!allowed.isEmpty() && !allowed.contains(event.source())) {
                    out.error("命令 " + event.commandName() + " 不允许来自来源 " + event.source());
                    return CommandResult.exit(CommandResult.FAILURE);
                }
            }
            ParsedArgs parsed;
            try {
                parsed = parse(command, event);
            } catch (IllegalArgumentException e) {
                out.error(e.getMessage());
                return CommandResult.exit(CommandResult.FAILURE);
            }
            Invocation inv = new Invocation(command, parsed, out, event.source(), state);
            try {
                return command.execute(inv);
            } catch (Exception e) {
                out.error("命令 " + command.name() + " 执行失败: " + e.getMessage());
                return CommandResult.exit(CommandResult.FAILURE);
            }
        } finally {
            dispatchLock.unlock();
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
}
