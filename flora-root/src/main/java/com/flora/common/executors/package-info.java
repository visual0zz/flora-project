/**
 * 共享并发设施：按任务语义分池的线程池。
 * <p>本包为 flora-root 内部实现，<b>不对外导出</b>；外部代码不应依赖其中的类型。
 * 内部组件通过 {@link com.flora.common.executors.InternalExecutors} 取用 {@link com.flora.common.executors.TaskKind}
 * 指定的共享执行器；实现细节位于 {@code com.flora.common.executors.impl}。</p>
 */
package com.flora.common.executors;
