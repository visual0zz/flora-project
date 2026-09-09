package com.flora.sanctum.app.ui;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * GUI 侧的轻量变更事件总线：模型（core）只负责数据读写，UI 在每次"会改数据结构"的
 * 操作后通过 {@link #markDirty()} 登记一次脏标记，随后由 {@link #refresh()} 统一触发一次
 * 树 + 列表重建。
 * <p>
 * 设计为 UI 内部机制：不直接耦合 core 的事件，避免侵入核心模型。监听器在 Swing EDT 上调用。
 */
final class ModelChangeBus {

    private static final Logger LOG = LoggerFactory.getLogger(ModelChangeBus.class);

    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private boolean dirty;

    /** 注册刷新监听（重建树 + 列表）。 */
    void subscribe(Runnable listener) {
        listeners.add(listener);
        LOG.debug("ModelChangeBus subscribed, listener count={}", listeners.size());
    }

    /** 标记模型已变更（一次操作可多次调用，仅触发一次刷新）。 */
    void markDirty() {
        dirty = true;
    }

    /**
     * 若自上次刷新后有变更，触发一次刷新；否则为 no-op。
     * 多个连续 {@link #markDirty()} 只重建一次，避免重复开销。
     */
    void refresh() {
        if (!dirty) {
            return;
        }
        dirty = false;
        LOG.debug("ModelChangeBus refresh triggered, listeners={}", listeners.size());
        for (Runnable l : listeners) {
            l.run();
        }
    }
}
