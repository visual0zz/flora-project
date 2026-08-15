package com.flora.shell;

/**
 * 命令观察 sink：{@link CommandService#newSink} 返回的注册句柄。
 * <p>创建后，每次命令执行完毕都会把 {@link InputEvent} 与 {@link CommandResult} 交给其
 * 观察者；调用 {@link #close()} 后该 sink 被移除，不再接收后续回调。</p>
 */
public final class CommandSink {

    private final CommandObserver observer;
    private final Runnable closeAction;
    private boolean closed;

    CommandSink(CommandObserver observer, Runnable closeAction) {
        this.observer = observer;
        this.closeAction = closeAction;
    }

    /**
     * 关闭并移除该 sink；之后的命令执行不再回调此观察者。幂等。
     */
    public synchronized void close() {
        if (!closed) {
            closed = true;
            closeAction.run();
        }
    }

    /**
     * @return 该 sink 的观察者
     */
    public CommandObserver observer() {
        return observer;
    }

    /**
     * @return 是否已关闭
     */
    public synchronized boolean isClosed() {
        return closed;
    }
}
