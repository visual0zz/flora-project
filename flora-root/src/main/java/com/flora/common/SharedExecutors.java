package com.flora.common;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * flora-root 模块内部共享的线程池。
 * <p>供模块自身各组件（如缓存后台刷新）共用一个执行器，避免各处自行创建线程池。
 * 线程为守护线程，不会阻止 JVM 退出；生命周期随 JVM，无需调用方关闭。</p>
 */
public final class SharedExecutors {

    private static final ExecutorService REFRESH = Executors.newSingleThreadExecutor(daemon("flora-shared-refresh"));

    private SharedExecutors() {
    }

    /** 共享后台刷新执行器：单线程、串行执行刷新任务。 */
    public static Executor refresh() {
        return REFRESH;
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
