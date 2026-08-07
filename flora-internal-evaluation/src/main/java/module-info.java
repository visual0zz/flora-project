/**
 * internal-evaluation 内部评测模块。
 * <p>
 * 基于 JMH 的微基准测试与内部性能评测，不对外导出任何 API。
 * 依赖 flora-root 与 jmh.core。
 */
open module com.flora.internal.evaluation {
    requires com.flora.root;
    requires jmh.core;
}
