package com.flora.codegen.engine.ast;

/** 循环 <#break> 信号——通知 ForNode 立即退出循环。 */
public final class BreakSignal extends RuntimeException {
    public int remaining;

    public BreakSignal(int depth) {
        this.remaining = depth;
    }

    @Override public Throwable fillInStackTrace() { return this; }
}
