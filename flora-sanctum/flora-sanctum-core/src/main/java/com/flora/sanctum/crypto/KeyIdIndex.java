package com.flora.sanctum.crypto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * keyId 增量索引（见设计 02"可定位"）。
 * <p>
 * 对每个候选 DEK，遍历 byte1 的全部 256 个值，各算一次
 * {@code byte2 = SHA256(DEK‖byte1)[0:3]}，得到该 DEK 的 256 个 keyId，
 * 登记进 {@code map<4字节keyId, list<DEK>>}。读取某块时用其 keyId 查表，
 * 得候选 DEK 集合（通常 1 个，跨 DEK 碰撞时少数个），再以 GCM-SIV tag 试解确证。
 * <p>
 * 索引随解锁动态增长：新 DEK 一到即建条目并立即可用，不要求一次性建全。
 * 本类不持有 DEK 明文以外的持久状态；锁定后丢弃。
 */
public final class KeyIdIndex {

    private final Map<Integer, List<byte[]>> index = new HashMap<>();

    /** 登记一个 DEK，为其建立 256 个 keyId 条目。 */
    public void register(byte[] dek) {
        // 遍历 byte1 全部 256 个值；与 CipherCodec.makeKeyId 一致：hash=SHA256(dek‖byte1)
        for (int b1 = 0; b1 < 256; b1++) {
            byte[] m = new byte[dek.length + 1];
            System.arraycopy(dek, 0, m, 0, dek.length);
            m[dek.length] = (byte) b1;
            byte[] h = sha256(m);
            byte[] keyId = new byte[4];
            keyId[0] = (byte) b1;
            System.arraycopy(h, 0, keyId, 1, 3);
            int key = toInt(keyId);
            index.computeIfAbsent(key, k -> new ArrayList<>()).add(dek.clone());
        }
    }

    /** 按 keyId 查候选 DEK 列表（可能多个，需试解确证）。 */
    public List<byte[]> lookup(byte[] keyId) {
        if (keyId.length != 4) {
            throw new IllegalArgumentException("keyId must be 4 bytes");
        }
        List<byte[]> deks = index.get(toInt(keyId));
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
    public int candidateCount(byte[] keyId) {
        return lookup(keyId).size();
    }

    private static int toInt(byte[] b) {
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    private static byte[] sha256(byte[] in) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(in);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 清空（锁定/解锁失败时）。 */
    public void clear() {
        index.clear();
    }

    public int size() {
        return index.size();
    }

    @Override
    public String toString() {
        return "KeyIdIndex{entries=" + index.size() + "}";
    }
}
