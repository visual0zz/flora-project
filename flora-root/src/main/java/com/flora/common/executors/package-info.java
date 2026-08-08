/**
 * 共享并发设施：按任务语义分池的线程池注册表。
 * <p>通过 {@link com.flora.common.executors.ExecutorPools} 取用 {@link com.flora.common.executors.TaskKind}
 * 指定的共享执行器；内部实现位于 {@code com.flora.common.executors.impl}（不导出）。</p>
 */
package com.flora.common.executors;
