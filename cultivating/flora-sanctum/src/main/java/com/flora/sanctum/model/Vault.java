package com.flora.sanctum.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 保险库聚合根。
 * <p>
 * 包含元数据和凭据条目列表。元数据明文存储，条目逐一加密存储。
 */
public class Vault {

    private VaultMeta meta;
    private List<Entry> entries;

    public Vault() {
        this.meta = new VaultMeta();
        this.entries = new ArrayList<>();
    }

    public Vault(VaultMeta meta, List<Entry> entries) {
        this.meta = meta;
        this.entries = entries;
    }

    public VaultMeta getMeta() { return meta; }
    public void setMeta(VaultMeta meta) { this.meta = meta; }

    public List<Entry> getEntries() { return entries; }
    public void setEntries(List<Entry> entries) { this.entries = entries; }

    /** 按 ID 查找条目。 */
    public Entry findEntryById(String id) {
        for (Entry e : entries) {
            if (e.getId().equals(id)) return e;
        }
        return null;
    }

    /** 添加或更新条目（按 ID 匹配）。 */
    public void putEntry(Entry entry) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId().equals(entry.getId())) {
                entry.touch();
                entries.set(i, entry);
                return;
            }
        }
        entries.add(entry);
    }

    /** 按 ID 删除条目。 */
    public boolean removeEntry(String id) {
        return entries.removeIf(e -> e.getId().equals(id));
    }
}
