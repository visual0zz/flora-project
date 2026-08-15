package com.flora.shell;

import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonString;
import com.flora.root.codec.json.model.JsonValue;
import com.flora.shell.spec.ArgSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandComponentTest {

    private static final class EchoCommand implements Command {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "回显文本";
        }

        @Override
        public List<ArgSpec> args() {
            return List.of(ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("text")
                    .required(true).description("要回显的文本").build());
        }

        @Override
        public CommandResult execute(Invocation ctx) {
            return CommandResult.data(new JsonString("echo:" + ctx.args().get("text").asString()));
        }
    }

    @Test
    void registersAndDispatches() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new EchoCommand());
        CommandResult result = commandService.submit(
                InputEvent.ofArgs(UsageScenario.CLI, "echo", "hello"));
        assertEquals(CommandResult.Status.SUCCESS, result.status());
        assertEquals("echo:hello", result.data().asString());
    }

    @Test
    void unknownCommandFails() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        CommandResult result = commandService.submit(
                InputEvent.ofArgs(UsageScenario.CLI, "nope"));
        assertEquals(CommandResult.Status.SYSTEM_ERROR, result.status());
    }

    @Test
    void builtinHelpIsRegistered() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        assertNotNull(commandService.find("help"));
    }

    @Test
    void userCommandOverridesBuiltinByPriority() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        Command override = new HelpOverride();
        commandService.register(override);
        assertEquals(override, commandService.find("help"));
    }

    @Test
    void newSinkReceivesExecutedEvent() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new EchoCommand());
        List<InputEvent> events = new ArrayList<>();
        List<CommandResult> results = new ArrayList<>();
        CommandSink sink = commandService.newSink((event, result) -> {
            events.add(event);
            results.add(result);
        });
        InputEvent event = InputEvent.ofArgs(UsageScenario.CLI, "echo", "fan");
        commandService.submit(event);
        assertEquals(1, events.size());
        assertEquals("echo", events.get(0).commandName());
        assertEquals(1, results.size());
        assertEquals("echo:fan", results.get(0).data().asString());
        assertEquals(CommandResult.Status.SUCCESS, results.get(0).status());
    }

    @Test
    void sinkCloseStopsNotifications() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new EchoCommand());
        List<String> seen = new ArrayList<>();
        CommandSink sink = commandService.newSink((event, result) ->
                seen.add(result.data().asString()));
        commandService.submit(InputEvent.ofArgs(UsageScenario.CLI, "echo", "one"));
        sink.close();
        commandService.submit(InputEvent.ofArgs(UsageScenario.CLI, "echo", "two"));
        assertEquals(List.of("echo:one"), seen);
    }

    @Test
    void sinkExceptionDoesNotAffectOtherSinks() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new EchoCommand());
        List<String> seen = new ArrayList<>();
        // 第一个 sink 抛异常，不应影响第二个 sink 与主流程
        commandService.newSink((event, result) -> {
            throw new IllegalStateException("模拟 sink 异常");
        });
        commandService.newSink((event, result) -> seen.add(result.data().asString()));
        CommandResult result = commandService.submit(
                InputEvent.ofArgs(UsageScenario.CLI, "echo", "iso"));
        assertEquals(CommandResult.Status.SUCCESS, result.status());
        assertEquals(List.of("echo:iso"), seen);
    }

    @Test
    void scenarioFilterRejectsUnsupportedCommand() {
        // RestrictedCommand 仅声明支持 AGENT 场景，不能注册进 CLI 场景的组件
        CommandService cliService = new CommandService(UsageScenario.CLI);
        assertThrows(IllegalArgumentException.class, () -> cliService.register(new RestrictedCommand()));
        // 注册进 AGENT 场景的组件则允许
        CommandService agentService = new CommandService(UsageScenario.AGENT);
        agentService.register(new RestrictedCommand());
        assertEquals(CommandResult.Status.SUCCESS, agentService.submit(
                InputEvent.ofJson(UsageScenario.AGENT, "restricted", new JsonObject())).status());
    }

    @Test
    void cliArgsRunAndReturnExitCode() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new EchoCommand());
        assertEquals(CommandResult.Status.SUCCESS, commandService
                .submit(InputEvent.ofCliArgs(List.of("echo", "x"))).status());
    }

    @Test
    void cliArgsEmptyThrows() {
        // ofCliArgs 需要至少一个命令名；空参数由工具自行判断
        assertThrows(IllegalArgumentException.class, () -> InputEvent.ofCliArgs(List.of()));
    }

    @Test
    void helpIsARealCommand() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new EchoCommand());
        // help 是注册的命令，可被显式调用（返回成功并渲染）
        assertEquals(CommandResult.Status.SUCCESS, commandService
                .submit(InputEvent.ofCliArgs(List.of("help"))).status());
    }

    @Test
    void helpFlagIsNotInterceptedByDefault() {
        // 框架不再默认拦截 --help：它会被当作命令名，未注册则失败
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new EchoCommand());
        assertEquals(CommandResult.Status.SYSTEM_ERROR, commandService
                .submit(InputEvent.ofCliArgs(List.of("--help"))).status());
    }

    @Test
    void unknownCommandReturnsFailure() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        assertEquals(CommandResult.Status.SYSTEM_ERROR, commandService
                .submit(InputEvent.ofCliArgs(List.of("nope"))).status());
    }

    @Test
    void aliasForwardsToTargetWithAppendedArgs() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new EchoCommand());
        commandService.setAlias("ec", "echo", List.of());
        CommandResult result = commandService.submit(
                InputEvent.ofArgs(UsageScenario.CLI, "ec", "hello"));
        assertEquals("echo:hello", result.data().asString());
    }

    @Test
    void aliasPrefixArgsArePrepended() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new JoinCommand());
        // alias ga -> join hello ；调用 ga world 等价 join hello world
        commandService.setAlias("ga", "join", List.of("hello"));
        CommandResult result = commandService.submit(
                InputEvent.ofArgs(UsageScenario.CLI, "ga", "world"));
        assertEquals("join:hello world", result.data().asString());
    }

    @Test
    void aliasCommandRegistersAlias() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new EchoCommand());
        CommandResult result = commandService.submit(
                InputEvent.ofArgs(UsageScenario.CLI, "alias", "e", "echo"));
        assertEquals(CommandResult.Status.SUCCESS, result.status());
        assertEquals("echo", commandService.aliases().get("e").target());
    }

    @Test
    void forwardingViaInvocation() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new EchoCommand());
        commandService.register(new ForwardingCommand());
        CommandResult result = commandService.submit(
                InputEvent.ofArgs(UsageScenario.CLI, "forwarder", "ping"));
        assertEquals("echo:ping", result.data().asString());
    }

    @Test
    void aliasRecursionIsLimited() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.setAlias("a", "b", List.of());
        commandService.setAlias("b", "a", List.of());
        // 别名环 a->b->a... 应被深度上限拦截，不无限递归
        CommandResult result = commandService.submit(
                InputEvent.ofArgs(UsageScenario.CLI, "a"));
        assertEquals(CommandResult.Status.SYSTEM_ERROR, result.status());
    }

    @Test
    void successfulCommandHasStatusSuccess() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new EchoCommand());
        CommandResult result = commandService.submit(
                InputEvent.ofArgs(UsageScenario.CLI, "echo", "hi"));
        assertEquals(CommandResult.Status.SUCCESS, result.status());
    }

    @Test
    void commandErrorHasStatusCommandError() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new EchoCommand());
        // echo 需要必选位置参数 text，缺失触发参数错误 → COMMAND_ERROR
        CommandResult result = commandService.submit(
                InputEvent.ofArgs(UsageScenario.CLI, "echo"));
        assertEquals(CommandResult.Status.COMMAND_ERROR, result.status());
    }

    @Test
    void unknownCommandHasStatusSystemError() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        CommandResult result = commandService.submit(
                InputEvent.ofArgs(UsageScenario.CLI, "nope"));
        assertEquals(CommandResult.Status.SYSTEM_ERROR, result.status());
    }

    @Test
    void executionExceptionHasStatusSystemError() {
        CommandService commandService = new CommandService(UsageScenario.CLI);
        commandService.register(new ThrowingCommand());
        CommandResult result = commandService.submit(
                InputEvent.ofArgs(UsageScenario.CLI, "boom"));
        assertEquals(CommandResult.Status.SYSTEM_ERROR, result.status());
    }

    /** 故意抛异常的命令，验证执行异常归为 SYSTEM_ERROR。 */
    private static final class ThrowingCommand implements Command {
        @Override
        public String name() {
            return "boom";
        }

        @Override
        public String description() {
            return "抛异常命令";
        }

        @Override
        public CommandResult execute(Invocation ctx) {
            throw new IllegalStateException("模拟执行异常");
        }
    }

    /** 把请求转给 echo 的命令，验证 Invocation.forward 走完整分派管线。 */
    private static final class ForwardingCommand implements Command {
        @Override
        public String name() {
            return "forwarder";
        }

        @Override
        public String description() {
            return "转发到 echo";
        }

        @Override
        public List<ArgSpec> args() {
            return List.of(ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("text")
                    .required(true).description("要转发的文本").build());
        }

        @Override
        public CommandResult execute(Invocation ctx) {
            String text = ctx.args().get("text").asString();
            return ctx.forward("echo", List.of(text));
        }
    }

    /** 把全部位置参数用空格连接，用于验证别名前缀参数的拼接。 */
    private static final class JoinCommand implements Command {
        @Override
        public String name() {
            return "join";
        }

        @Override
        public String description() {
            return "拼接参数";
        }

        @Override
        public List<ArgSpec> args() {
            return List.of(ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("words")
                    .variadic(true).type(ArgSpec.Type.STRING_LIST).description("要拼接的词").build());
        }

        @Override
        public CommandResult execute(Invocation ctx) {
            List<String> words = new ArrayList<>();
            JsonValue v = ctx.args().get("words");
            if (v != null && !v.isNull()) {
                var arr = v.asArray();
                for (int i = 0; i < arr.size(); i++) {
                    words.add(arr.get(i).asString());
                }
            }
            return CommandResult.data(new JsonString("join:" + String.join(" ", words)));
        }
    }

    /** 低优先级覆写内置 help 的命令（本应高于 -100）。 */
    private static final class HelpOverride implements Command {
        @Override
        public String name() {
            return "help";
        }

        @Override
        public String description() {
            return "覆写版 help";
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public CommandResult execute(Invocation ctx) {
            return CommandResult.success();
        }
    }

    private static final class RestrictedCommand implements Command {
        @Override
        public String name() {
            return "restricted";
        }

        @Override
        public String description() {
            return "仅限 AGENT 场景的命令";
        }

        @Override
        public List<UsageScenario> usageScenarios() {
            return List.of(UsageScenario.AGENT);
        }

        @Override
        public CommandResult execute(Invocation ctx) {
            return CommandResult.success();
        }
    }
}
