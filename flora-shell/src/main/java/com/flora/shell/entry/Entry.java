package com.flora.shell.entry;

import com.flora.java.CheckUtil;
import com.flora.shell.ChannelId;
import com.flora.shell.Command;
import com.flora.shell.CommandComponent;
import com.flora.shell.CommandResult;
import com.flora.shell.InputEvent;
import com.flora.shell.help.HelpRenderer;

import java.util.Arrays;
import java.util.List;

/**
 * 一次性命令行入口壳：把 argv 变成调用提交给指令组件，按退出码结束进程。
 * <p>框架不提供驻留逻辑：{@link #run} 执行完即返回退出码。
 * argv 为空时默认报错（帮助渲染到 stderr、非零退出码），与现有工具"无参数打印用法退出"一致。</p>
 */
public final class Entry {

    private Entry() {
    }

    /**
     * 一次性执行：解析 argv → 提交 → 返回退出码。
     * <p>规则：</p>
     * <ul>
     *   <li>argv 为空 → 帮助到 stderr、退出码 1；</li>
     *   <li>第一个参数是命令名，其余是其参数；</li>
     *   <li>{@code help} 或 {@code --help} 作为唯一参数 → 全局帮助到 stdout、退出码 0。</li>
     * </ul>
     *
     * @param component 指令组件
     * @param args      进程参数（不含命令名本身）
     * @param state     调用方领域状态
     * @return 进程退出码
     */
    public static int run(CommandComponent component, String[] args, Object state) {
        CheckUtil.notNull(component, "指令组件不能为空");
        List<String> argv = args == null ? List.of() : Arrays.asList(args);

        // 无参数：报错 + 帮助到 stderr
        if (argv.isEmpty()) {
            System.err.println("缺少命令（输入 help 查看可用命令）");
            System.err.print(new HelpRenderer().renderGlobal(component.commands()));
            return CommandResult.FAILURE;
        }

        String commandName = argv.get(0);
        List<String> rest = argv.subList(1, argv.size());

        // --help 拦截：转为全局帮助（本期批量的两条 help 入口：help / --help）
        if ("--help".equals(commandName) || "-h".equals(commandName)) {
            System.out.print(new HelpRenderer().renderGlobal(component.commands()));
            return CommandResult.SUCCESS;
        }

        // CliView 前置校验
        Command command = component.find(commandName);
        if (command instanceof Command.CliView cliView) {
            String err = cliView.beforeExecute(rest);
            if (err != null) {
                System.err.println(err);
                return CommandResult.FAILURE;
            }
        }

        InputEvent event = InputEvent.ofArgv(ChannelId.ARGV, commandName, rest);
        return component.submit(event, state).exitCode();
    }
}
