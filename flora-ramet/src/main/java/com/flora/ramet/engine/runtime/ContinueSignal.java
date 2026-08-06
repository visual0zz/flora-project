package com.flora.ramet.engine.runtime;

/** 循环 <#continue> 信号——通知 ForNode 跳过当前迭代的剩余体。 */
public final class ContinueSignal extends RuntimeException {
    public int remaining;

    public ContinueSignal(int depth) {
        this.remaining = depth;
    }

    @Override public Throwable fillInStackTrace() { return this; }
}
