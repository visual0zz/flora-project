package com.flora.sanctum.core.model.tree;
import com.flora.sanctum.core.model.*;
import com.flora.sanctum.core.model.impl.*;
import com.flora.sanctum.core.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.entropy.HashUtil;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图标树：自定义图标对象（IconNode）。
 * 用唯一根（data）DEK 加密，parent 指向根对象 uuid。
 * <p>
 * 去重：{@link #findOrCreate(String, byte[], String)} 按图标字节内容（SHA-256）复用已有图标，
 * 内容相同的图标在库内只存一份，重复导入同一份文件不会产生副本。
 * 索引只在首次需要时构建（遍历已有图标一次），不落盘、不影响存储格式，老库直接受益。
 * {@link #createIcon(String, byte[], String)} 保持「总是新建」的语义不变。</p>
 */
public final class IconTree extends DataTree {

    /** 内容哈希（hex）→ 图标 uuid 的索引；null 表示尚未构建。 */
    private volatile Map<String, UUID> contentIndex;

    public IconTree(TreeContext ctx) {
        super(ViewNodeType.ICON, ctx);
    }

    @Override
    protected boolean belongsTo(StoredNodeType type, String kind) {
        return type == StoredNodeType.ICON;
    }

    @Override
    public IconNode find(UUID uuid) {
        JsonObject d = context().read(uuid);
        if (!isOwned(d)) {
            return null;
        }
        return new IconNode(uuid, this);
    }

    public List<IconNode> icons() {
        List<IconNode> out = new ArrayList<>();
        for (TreeNode n : nodes()) {
            out.add((IconNode) n);
        }
        return out;
    }

    public IconNode createIcon(String name, byte[] data, String format) {
        UUID iconUuid = UUID.randomUUID();
        JsonObject icon = new JsonObject();
        icon.put("type", StoredNodeType.ICON.tag());
        icon.put("parent", com.flora.sanctum.core.util.UuidHex.toHex(context().vault().rootObjectUuid()));
        if (name != null && !name.isBlank()) {
            icon.put("name", name);
        }
        icon.put("data", Base64.getEncoder().encodeToString(data));
        icon.put("format", format);
        byte[] dek = context().vault().rootDek();
        context().writeWithDek(iconUuid, icon, dek);
        // 索引已构建时同步登记，使后续 findOrCreate 能命中刚写入的图标
        Map<String, UUID> idx = contentIndex;
        if (idx != null && data != null && data.length > 0) {
            idx.putIfAbsent(contentHash(data), iconUuid);
        }
        return new IconNode(iconUuid, this);
    }

    /**
     * 按内容去重地取图标：库内已存在字节完全相同的图标则复用它，否则新建。
     * <p>复用时保留已有图标的 name / format（可能已有条目在引用它），不因本次传入的名字而改名。
     * 字节为空/为 null 时不参与去重（退化为新建），避免无意义地把空图标折叠成一个。</p>
     *
     * @param name   图标名称（新建时使用；复用时被忽略）
     * @param data   图标原始字节
     * @param format 图像格式（新建时使用；复用时被忽略）
     */
    public IconNode findOrCreate(String name, byte[] data, String format) {
        if (data == null || data.length == 0) {
            return createIcon(name, data, format);
        }
        String key = contentHash(data);
        UUID hit = index().get(key);
        if (hit != null) {
            IconNode existing = find(hit);
            if (existing != null && !existing.deleted()) {
                return existing;
            }
            // 索引陈旧（该图标已被删除）：剔除后按新建处理，并把新节点登记回索引
            index().remove(key);
        }
        return createIcon(name, data, format);
    }

    /** 内容索引（首次使用时构建：遍历已有图标一次，按内容哈希登记）。 */
    private Map<String, UUID> index() {
        Map<String, UUID> idx = contentIndex;
        if (idx == null) {
            Map<String, UUID> built = new ConcurrentHashMap<>();
            for (IconNode icon : icons()) {
                byte[] data = icon.iconData();
                if (data.length > 0) {
                    built.putIfAbsent(contentHash(data), icon.uuid());
                }
            }
            contentIndex = built;
            idx = built;
        }
        return idx;
    }

    /** 图标字节的内容哈希（SHA-256 hex），用于去重比对。 */
    private static String contentHash(byte[] data) {
        return java.util.HexFormat.of().formatHex(HashUtil.sha256(data));
    }
}
