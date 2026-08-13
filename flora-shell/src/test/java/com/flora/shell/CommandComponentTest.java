package com.flora.shell;

import com.flora.shell.output.OutputSink;
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
        public String allowedSourcePattern() {
            return ".*";
        }

        @Override
        public List<ArgSpec> args() {
            return List.of(ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("text")
                    .required(true).description("要回显的文本").build());
        }

        @Override
        public CommandResult execute(Invocation ctx) {
            ctx.out().println("echo: " + ctx.args().get("text"));
            return CommandResult.data("echo:" + ctx.args().get("text"));
        }
    }

    @Test
    void registersAndDispatches() {
        CommandService commandService = new CommandService();
        commandService.register(new EchoCommand());
        CommandResult result = commandService.submit(
                InputEvent.ofArgv(ChannelId.ARGV, "echo", List.of("hello")));
        assertEquals(CommandResult.SUCCESS, result.exitCode());
        assertEquals("echo:hello", result.data());
    }

    @Test
    void unknownCommandFails() {
        CommandService commandService = new CommandService();
        CommandResult result = commandService.submit(
                InputEvent.ofArgv(ChannelId.ARGV, "nope", List.of()));
        assertEquals(CommandResult.FAILURE, result.exitCode());
    }

    @Test
    void builtinHelpIsRegistered() {
        CommandService commandService = new CommandService();
        assertNotNull(commandService.find("help"));
    }

    @Test
    void userCommandOverridesBuiltinByPriority() {
        CommandService commandService = new CommandService();
        Command override = new HelpOverride();
        commandService.register(override);
        assertEquals(override, commandService.find("help"));
    }

    @Test
    void outputFansOutToSinks() {
        CommandService commandService = new CommandService();
        commandService.register(new EchoCommand());
        List<String> received = new ArrayList<>();
        commandService.attach(new OutputSink() {
            @Override
            public void emit(String text) {
                received.add(text);
            }

            @Override
            public void emitError(String text) {
            }
        });
        commandService.submit(InputEvent.ofArgv(ChannelId.ARGV, "echo", List.of("fan")));
        assertEquals(List.of("echo: fan\n"), received);
    }

    @Test
    void sourcePatternRejectsDisallowedSource() {
        CommandService commandService = new CommandService();
        commandService.register(new RestrictedCommand());
        // AGENT 匹配命令声明的正则，允许
        assertEquals(CommandResult.SUCCESS, commandService.submit(
                InputEvent.ofStructured(ChannelId.AGENT, "restricted", java.util.Map.of())).exitCode());
        // ARGV 不匹配命令声明的正则，拒绝
        assertEquals(CommandResult.FAILURE, commandService.submit(
                InputEvent.ofArgv(ChannelId.ARGV, "restricted", List.of())).exitCode());
    }

    @Test
    void cliArgsRunAndReturnExitCode() {
        CommandService commandService = new CommandService();
        commandService.register(new EchoCommand());
        int exitCode = commandService.submit(InputEvent.ofCliArgs(List.of("echo", "x"))).exitCode();
        assertEquals(CommandResult.SUCCESS, exitCode);
    }

    @Test
    void cliArgsEmptyThrows() {
        // ofCliArgs 需要至少一个命令名；空参数由工具自行判断
        assertThrows(IllegalArgumentException.class, () -> InputEvent.ofCliArgs(List.of()));
    }

    @Test
    void helpIsARealCommand() {
        CommandService commandService = new CommandService();
        commandService.register(new EchoCommand());
        // help 是注册的命令，可被显式调用（返回成功并渲染）
        int exitCode = commandService.submit(InputEvent.ofCliArgs(List.of("help"))).exitCode();
        assertEquals(CommandResult.SUCCESS, exitCode);
    }

    @Test
    void helpFlagIsNotInterceptedByDefault() {
        // 框架不再默认拦截 --help：它会被当作命令名，未注册则失败
        CommandService commandService = new CommandService();
        commandService.register(new EchoCommand());
        int exitCode = commandService.submit(InputEvent.ofCliArgs(List.of("--help"))).exitCode();
        assertEquals(CommandResult.FAILURE, exitCode);
    }

    @Test
    void unknownCommandReturnsFailure() {
        CommandService commandService = new CommandService();
        int exitCode = commandService.submit(InputEvent.ofCliArgs(List.of("nope"))).exitCode();
        assertEquals(CommandResult.FAILURE, exitCode);
    }

    @Test
    void aliasForwardsToTargetWithAppendedArgs() {
        CommandService commandService = new CommandService();
        commandService.register(new EchoCommand());
        commandService.setAlias("ec", "echo", List.of());
        CommandResult result = commandService.submit(
                InputEvent.ofArgv(ChannelId.ARGV, "ec", List.of("hello")));
        assertEquals("echo:hello", result.data());
    }

    @Test
    void aliasPrefixArgsArePrepended() {
        CommandService commandService = new CommandService();
        commandService.register(new JoinCommand());
        // alias ga -> join hello ；调用 ga world 等价 join hello world
        commandService.setAlias("ga", "join", List.of("hello"));
        CommandResult result = commandService.submit(
                InputEvent.ofArgv(ChannelId.ARGV, "ga", List.of("world")));
        assertEquals("join:hello world", result.data());
    }

    @Test
    void aliasCommandRegistersAlias() {
        CommandService commandService = new CommandService();
        commandService.register(new EchoCommand());
        CommandResult result = commandService.submit(
                InputEvent.ofArgv(ChannelId.ARGV, "alias", List.of("e", "echo")));
        assertEquals(CommandResult.SUCCESS, result.exitCode());
        assertEquals("echo", commandService.aliases().get("e").target());
    }

    @Test
    void forwardingViaInvocation() {
        CommandService commandService = new CommandService();
        commandService.register(new EchoCommand());
        commandService.register(new ForwardingCommand());
        CommandResult result = commandService.submit(
                InputEvent.ofArgv(ChannelId.ARGV, "forwarder", List.of("ping")));
        assertEquals("echo:ping", result.data());
    }

    @Test
    void aliasRecursionIsLimited() {
        CommandService commandService = new CommandService();
        commandService.setAlias("a", "b", List.of());
        commandService.setAlias("b", "a", List.of());
        // 别名环 a->b->a... 应被深度上限拦截，不无限递归
        CommandResult result = commandService.submit(
                InputEvent.ofArgv(ChannelId.ARGV, "a", List.of()));
        assertEquals(CommandResult.FAILURE, result.exitCode());
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
        public String allowedSourcePattern() {
            return ".*";
        }

        @Override
        public List<ArgSpec> args() {
            return List.of(ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("text")
                    .required(true).description("要转发的文本").build());
        }

        @Override
        public CommandResult execute(Invocation ctx) {
            String text = ctx.args().get("text");
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
        public String allowedSourcePattern() {
            return ".*";
        }

        @Override
        public List<ArgSpec> args() {
            return List.of(ArgSpec.builder().kind(ArgSpec.Kind.POSITIONAL).name("words")
                    .variadic(true).type(ArgSpec.Type.STRING_LIST).description("要拼接的词").build());
        }

        @Override
        public CommandResult execute(Invocation ctx) {
            String joined = String.join(" ", ctx.args().getStringList("words"));
            return CommandResult.data("join:" + joined);
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
        public String allowedSourcePattern() {
            return ".*";
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
            return "受限命令";
        }

        @Override
        public String allowedSourcePattern() {
            return "agent";
        }

        @Override
        public CommandResult execute(Invocation ctx) {
            return CommandResult.success();
        }
    }
}
