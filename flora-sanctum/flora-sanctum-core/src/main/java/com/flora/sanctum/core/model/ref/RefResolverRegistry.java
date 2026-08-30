package com.flora.sanctum.core.model.ref;

import com.flora.sanctum.core.model.Ref;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 引用解析器注册表：scheme 前缀 → 解析器。统一入口 {@link #referencedBlocks} 供 GC 遍历调用。
 */
public final class RefResolverRegistry {

    private final Map<String, RefResolver> byScheme = new HashMap<>();

    public RefResolverRegistry() {
        register(new NodeRefResolver());
        register(new BuiltinRefResolver());
    }

    public void register(RefResolver resolver) {
        byScheme.put(resolver.scheme(), resolver);
    }

    /** 该引用指向的对象库块 uuid 集合（GC 可达性）。未知 scheme 抛异常。 */
    public Set<UUID> referencedBlocks(Ref ref) {
        RefResolver resolver = byScheme.get(ref.scheme());
        if (resolver == null) {
            throw new IllegalArgumentException("unknown ref scheme: " + ref.scheme());
        }
        return resolver.referencedBlocks(ref);
    }
}
