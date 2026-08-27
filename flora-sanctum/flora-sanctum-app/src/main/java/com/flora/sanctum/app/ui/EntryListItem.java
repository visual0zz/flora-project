package com.flora.sanctum.app.ui;

import com.flora.sanctum.model.StoredNodeType;

import java.util.UUID;

/**
 * 条目列表中一项的域对象封装（替代过去的并行数组 entryUuids/listItemTypes/listItemIcons）。
 * 渲染器、双击导航、选中解析直接读此对象，消除"按索引在三个平行数组间对齐"的易错模式。
 *
 * @param uuid     节点 uuid（列表项为组/条目/图标/SSH/远程时均有）
 * @param type     存储类型，供图标与双击导航判断
 * @param display  列表展示文本（条目名、组名或"名 · 路径"）
 * @param iconRef  图标 id（"builtin:name" 或 uuid；无则 null），列表渲染优先使用
 */
record EntryListItem(UUID uuid, StoredNodeType type, String display, String iconRef) {
}
