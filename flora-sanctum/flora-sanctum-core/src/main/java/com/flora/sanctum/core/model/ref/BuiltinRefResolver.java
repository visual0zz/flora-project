package com.flora.sanctum.core.model.ref;

import com.flora.sanctum.core.model.Ref;

import java.util.Set;
import java.util.UUID;

/**
 * builtin 方案解析器：引用应用内置资源（图标等），不进对象库，
 * 因此不参与 GC 可达性（返回空集）。其资源名由 {@link Ref#id()} 携带，物化时按 kind 查内置资源表。
 */
public final class BuiltinRefResolver implements RefResolver {

    @Override
    public String scheme() {
        return "builtin";
    }

    @Override
    public Set<UUID> referencedBlocks(Ref ref) {
        return Set.of();
    }
}
