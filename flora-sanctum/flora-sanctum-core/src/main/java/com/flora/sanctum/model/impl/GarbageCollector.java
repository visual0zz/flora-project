package com.flora.sanctum.model.impl;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.store.Block;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 垃圾收集：从根集合（manifest 明文块 + parent 为根概念 tag 的块）出发，
 * 沿归属边(parent)与引用边(icon/keyRef)遍历，不可达的孤立块删除并返回其 uuid 列表。
 */
public final class GarbageCollector {

    private final TreeContext ctx;

    public GarbageCollector(TreeContext ctx) {
        this.ctx = ctx;
    }

    /** 收集并删除孤立块，返回被删除的 uuid 列表。 */
    public List<UUID> collect() {
        List<Block> blocks = ctx.store().scan();
        Set<UUID> reachable = new HashSet<>();
        // 根：manifest（明文块）+ 根对象（manifest.rootGroupUuid 定位，KEK 包裹）
        java.util.UUID rootUuid = ctx.vault().manifest().rootGroupUuid();
        for (Block b : blocks) {
            if (b.isPlaintext()) {
                reachable.add(b.uuid());
                continue;
            }
            if (rootUuid != null && rootUuid.equals(b.uuid())) {
                reachable.add(b.uuid());
                continue;
            }
        }
        // 沿 parent 链 + 引用边扩展
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Block b : blocks) {
                if (reachable.contains(b.uuid())) {
                    continue;
                }
                JsonObject n = nodeOf(b);
                if (n == null) {
                    continue;
                }
                String parent = n.getString("parent");
                String icon = n.getString("icon");
                String keyRef = n.getString("keyRef");
                if ((parent != null && isUuid(parent) && reachable.contains(UUID.fromString(parent)))
                        || (icon != null && reachable.contains(UUID.fromString(icon)))
                        || (keyRef != null && isUuid(keyRef) && reachable.contains(UUID.fromString(keyRef)))) {
                    reachable.add(b.uuid());
                    progress = true;
                }
            }
        }
        // 不可达 = 孤立 → 删除
        List<UUID> orphaned = new ArrayList<>();
        for (Block b : blocks) {
            if (!reachable.contains(b.uuid())) {
                ctx.delete(b.uuid());
                orphaned.add(b.uuid());
            }
        }
        return orphaned;
    }

    private JsonObject nodeOf(Block b) {
        byte[] plain = ctx.vault().resolve(b.masked(), b.timestampText());
        if (plain == null) {
            return null;
        }
        try {
            return JsonUtil.parseObject(new String(plain, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
