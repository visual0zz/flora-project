package com.flora.hanako.tools;

import com.flora.hanako.core.model.MemoryFact;
import com.flora.hanako.storage.TaggedFactStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 待办 / 经验工具：把用户的偏好、纠正、工作流沉淀为记忆事实（标签化）。
 * <p>复刻 openhanako 的 todo + experience 工具——既管理当前任务的待办，
 * 也把可复用经验写入记忆（{@link TaggedFactStore}）。</p>
 */
public final class TodoTool implements Tool {

    private final TaggedFactStore memory;

    public TodoTool(TaggedFactStore memory) {
        this.memory = memory;
    }

    @Override
    public String name() {
        return "remember";
    }

    @Override
    public String description() {
        return "把一条值得长期记住的事实/偏好/纠正写入记忆，附带标签便于日后检索。参数：text 事实文本，tags 标签列表。";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("text", ReadFileTool.strProp("要记住的事实或偏好文本"));
        Map<String, Object> tagsProp = new LinkedHashMap<>();
        tagsProp.put("type", "array");
        tagsProp.put("description", "分类标签，如 偏好/工作流/纠正");
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "string");
        tagsProp.put("items", items);
        props.put("tags", tagsProp);
        return ReadFileTool.objSchema(props, List.of("text"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        String text = ReadFileTool.asString(args.get("text"));
        if (text == null || text.isBlank()) {
            return "错误：缺少 text 参数";
        }
        List<String> tags = new java.util.ArrayList<>();
        Object rawTags = args.get("tags");
        if (rawTags instanceof List<?> list) {
            for (Object t : list) {
                if (t != null) {
                    tags.add(t.toString());
                }
            }
        }
        MemoryFact fact = new MemoryFact(UUID.randomUUID().toString(), text, tags);
        memory.put(fact);
        return "已记住（id=" + fact.getId() + "，标签=" + tags + "）";
    }
}
