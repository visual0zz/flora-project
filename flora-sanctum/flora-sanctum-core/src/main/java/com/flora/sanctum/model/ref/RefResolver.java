package com.flora.sanctum.model.ref;

import com.flora.sanctum.model.Ref;

import java.util.Set;
import java.util.UUID;

/**
 * 引用解析器：按 scheme 前缀注册（node / builtin），把 {@link Ref} 映射到对象库中的目标块。
 * <p>
 * 当前仅负责 GC 可达性判定（{@link #referencedBlocks}）：返回该引用指向的块 uuid 集合。
 * builtin 类引用不进对象库，返回空集；node 类引用返回其 id（被引用节点 uuid）。
 * 未来若要"物化"引用（取图标字节 / 取 key 对象），可在本接口扩展方法，解析器仍按 scheme 分派。
 */
public interface RefResolver {

    /** scheme 前缀（如 node / builtin），注册键。 */
    String scheme();

    /** 该引用指向的对象库块 uuid 集合（GC 可达性用）；无法定位返回空集。 */
    Set<UUID> referencedBlocks(Ref ref);
}
