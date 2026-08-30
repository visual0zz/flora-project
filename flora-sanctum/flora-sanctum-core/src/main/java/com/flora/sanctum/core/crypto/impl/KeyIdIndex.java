package com.flora.sanctum.core.crypto.impl;

import com.flora.sanctum.core.crypto.KeyIdDeriver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * keyId 索引（见设计"keyId 防关联"）。
 * <p>
 * 键为内部标识 {@code dekId = SHA256(DEK)[0:8]}（每 DEK 1 条，碰撞概率可忽略），
 * 值为 DEK 副本。读取某块时，用 {@link KeyIdDeriver#resolveDekId} 从密文头 (nonce, keyId)
 * 恢复 dekId 后查表，得候选 DEK 集合（通常 1 个），再以 GCM-SIV tag 试解确证。
 * <p>
 * 索引随解锁动态增长：新 DEK 一到即建条目并立即可用，不要求一次性建全。
 * 本类不持有 DEK 明文以外的持久状态；锁定后丢弃。
 */
public final class KeyIdIndex {

    private final Map<ByteKey, List<byte[]>> entries = new HashMap<>();

    /** 登记一个 DEK，建立其 dekId 索引条目。 */
    public void register(byte[] dek) {
        byte[] dekId = KeyIdDeriver.dekId(dek);
        entries.computeIfAbsent(new ByteKey(dekId), k -> new ArrayList<>()).add(dek.clone());
    }

    /** 按 dekId 查候选 DEK 列表（可能多个，需试解确证）。 */
    public List<byte[]> lookup(byte[] dekId) {
        if (dekId.length != Involution.FEISTEL_BLOCK_BYTES) {
            throw new IllegalArgumentException("dekId must be 8 bytes");
        }
        List<byte[]> deks = entries.get(new ByteKey(dekId));
        if (deks == null) {
            return List.of();
        }
        List<byte[]> copy = new ArrayList<>(deks.size());
        for (byte[] d : deks) {
            copy.add(d.clone());
        }
        return copy;
    }

    /** 候选 DEK 数（碰撞时 &gt;1）。 */
    public int candidateCount(byte[] dekId) {
        return lookup(dekId).size();
    }

    /** 清空并擦除内部 DEK 副本（锁定/解锁失败时，见设计 03"内存秘密清除"）。 */
    public void clear() {
        for (List<byte[]> list : entries.values()) {
            for (byte[] dek : list) {
                java.util.Arrays.fill(dek, (byte) 0);
            }
        }
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    @Override
    public String toString() {
        return "KeyIdIndex{entries=" + entries.size() + "}";
    }

    /** 字节数组键（不可变语义）。 */
    private static final class ByteKey {
        private final byte[] bytes;

        ByteKey(byte[] bytes) {
            this.bytes = bytes.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ByteKey k && Arrays.equals(bytes, k.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
