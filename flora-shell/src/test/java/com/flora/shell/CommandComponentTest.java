package com.flora.shell;

import com.flora.shell.entry.Entry;
import com.flora.shell.output.OutputSink;
import com.flora.shell.spec.ArgSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            ctx.out().println("echo: " + ctx.args().get("text"));
            return CommandResult.data("echo:" + ctx.args().get("text"));
        }
    }

    @Test
    void registersAndDispatches() {
        CommandService component = new CommandService();
        component.register(new EchoCommand());
        CommandResult result = component.submit(
                InputEvent.ofArgv(ChannelId.ARGV, "echo", List.of("hello")));
        assertEquals(CommandResult.SUCCESS, result.exitCode());
        assertEquals("echo:hello", result.data());
    }

    @Test
    void unknownCommandFails() {
        CommandService component = new CommandService();
        CommandResult result = component.submit(
                InputEvent.ofArgv(ChannelId.ARGV, "nope", List.of()));
        assertEquals(CommandResult.FAILURE, result.exitCode());
    }

    @Test
    void builtinHelpIsRegistered() {
        CommandService component = new CommandService();
        assertNotNull(component.find("help"));
    }

    @Test
    void userCommandOverridesBuiltinByPriority() {
        CommandService component = new CommandService();
        Command override = new HelpOverride();
        component.register(override);
        assertEquals(override, component.find("help"));
    }

    @Test
    void outputFansOutToSinks() {
        CommandService component = new CommandService();
        component.register(new EchoCommand());
        List<String> received = new ArrayList<>();
        component.attach(new OutputSink() {
            @Override
            public void emit(String text) {
                received.add(text);
            }

            @Override
            public void emitError(String text) {
            }
        });
        component.submit(InputEvent.ofArgv(ChannelId.ARGV, "echo", List.of("fan")));
        assertEquals(List.of("echo: fan\n"), received);
    }

    @Test
    void sourceRestrictedRejectsDisallowedSource() {
        CommandService component = new CommandService();
        component.register(new RestrictedCommand());
        // AGENT 在白名单内，允许
        assertEquals(CommandResult.SUCCESS, component.submit(
                InputEvent.ofStructured(ChannelId.AGENT, "restricted", java.util.Map.of())).exitCode());
        // ARGV 不在白名单内，拒绝
        assertEquals(CommandResult.FAILURE, component.submit(
                InputEvent.ofArgv(ChannelId.ARGV, "restricted", List.of())).exitCode());
    }

    @Test
    void entryRunsAndReturnsExitCode() {
        CommandService component = new CommandService();
        component.register(new EchoCommand());
        assertEquals(CommandResult.SUCCESS, Entry.run(component, new String[]{"echo", "x"}));
    }

    @Test
    void entryEmptyArgsFailsWithNonZero() {
        CommandService component = new CommandService();
        assertEquals(CommandResult.FAILURE, Entry.run(component, new String[]{}));
    }

    @Test
    void entryHelpFlagPrintsGlobalHelp() {
        CommandService component = new CommandService();
        component.register(new EchoCommand());
        assertEquals(CommandResult.SUCCESS, Entry.run(component, new String[]{"--help"}));
    }

    @Test
    void unknownCommandReturnsFailure() {
        CommandService component = new CommandService();
        assertEquals(CommandResult.FAILURE, Entry.run(component, new String[]{"nope"}));
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

    private static final class RestrictedCommand implements Command, Command.SourceRestricted {
        @Override
        public String name() {
            return "restricted";
        }

        @Override
        public String description() {
            return "受限命令";
        }

        @Override
        public Set<ChannelId> allowedSources() {
            return Set.of(ChannelId.AGENT);
        }

        @Override
        public CommandResult execute(Invocation ctx) {
            return CommandResult.success();
        }
    }
}
