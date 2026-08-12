package com.flora.shell.spec;

import com.flora.root.java.CheckUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 零依赖参数解析器。从一组 {@link ArgSpec} 声明解析 argv 序列或校验结构化 Map。
 * <p>唯一的统一入口是 {@link #parse(List)}（argv）与 {@link #validate(Map)}（结构化参数，
 * 如 Agent JSON 归一化后的对象）。两者落到同一套声明与校验逻辑：CLI 的 {@code --port 8080}
 * 与 Agent 的 {@code {"port":8080}} 语义一致。</p>
 * <p>解析失败抛出 {@link IllegalArgumentException}，消息给出缺哪个参数、哪个值非法、期望什么，
 * 由各接入方式按自己的方式呈现。</p>
 */
public final class ArgParser {

    private final List<ArgSpec> optionals = new ArrayList<>();
    private final List<ArgSpec> positionals = new ArrayList<>();
    private final List<List<String>> mutexGroups = new ArrayList<>();
    private final List<List<String>> oneOfGroups = new ArrayList<>();
    private final Map<String, ArgSpec> byLongName = new LinkedHashMap<>();
    private final Map<String, ArgSpec> byShortName = new LinkedHashMap<>();

    /**
     * @param specs 该命令的全部参数声明
     */
    public ArgParser(List<ArgSpec> specs) {
        for (ArgSpec spec : CheckUtil.notNull(specs, "参数声明列表不能为空")) {
            if (spec.kind() == ArgSpec.Kind.OPTION) {
                if (byLongName.put(spec.name(), spec) != null) {
                    throw new IllegalArgumentException("重复的选项名: --" + spec.name());
                }
                if (spec.shortName() != null && byShortName.put(spec.shortName(), spec) != null) {
                    throw new IllegalArgumentException("重复的选项短名: -" + spec.shortName());
                }
                optionals.add(spec);
            } else {
                positionals.add(spec);
            }
        }
        boolean sawVariadic = false;
        for (ArgSpec p : positionals) {
            if (sawVariadic) {
                throw new IllegalArgumentException("变长位置参数必须是最后一个: " + p.name());
            }
            if (p.variadic()) {
                sawVariadic = true;
            }
        }
    }

    /**
     * 添加一组互斥参数：同一调用中最多出现其中一个。
     *
     * @param names 参数名集合（不可为单个元素，否则无意义）
     */
    public ArgParser mutuallyExclusive(String... names) {
        CheckUtil.mustTrue(names != null && names.length > 1, "互斥组至少需要两个参数");
        mutexGroups.add(List.of(names));
        return this;
    }

    /**
     * 添加一组必选其一参数：同一调用中必须出现其中一个。
     *
     * @param names 参数名集合（不可为单个元素，否则无意义）
     */
    public ArgParser oneOf(String... names) {
        CheckUtil.mustTrue(names != null && names.length > 1, "必选其一组合至少需要两个参数");
        oneOfGroups.add(List.of(names));
        return this;
    }

    /**
     * 从 argv 序列解析参数。
     *
     * @param argv 命令行参数（不含命令名本身）
     * @return 解析结果
     * @throws IllegalArgumentException 参数缺失、非法或冲突时
     */
    public ParsedArgs parse(List<String> argv) {
        ParsedArgs out = new ParsedArgs();
        List<String> raw = new ArrayList<>(argv);
        // 先放默认值
        for (ArgSpec spec : optionals) {
            if (spec.defaultValue() != null) {
                out.put(spec.name(), convert(spec, spec.defaultValue()));
            }
        }

        int posIndex = 0;
        int i = 0;
        while (i < raw.size()) {
            String token = raw.get(i);
            if (isOption(token)) {
                String name = stripOption(token);
                ArgSpec spec = token.startsWith("--") ? byLongName.get(name) : byShortName.get(name);
                if (spec == null) {
                    throw new IllegalArgumentException("未知选项: " + token);
                }
                i = consumeOption(spec, raw, i, out);
            } else {
                // 位置参数
                if (posIndex >= positionals.size()) {
                    throw new IllegalArgumentException("多余的参数: " + token);
                }
                ArgSpec pos = positionals.get(posIndex);
                if (pos.variadic()) {
                    out.mutableList(pos.name()).addAll(raw.subList(i, raw.size()));
                    break;
                }
                out.put(pos.name(), convert(pos, token));
                posIndex++;
                i++;
            }
        }

        // 必选校验
        for (ArgSpec p : positionals) {
            if (p.required() && !out.contains(p.name())) {
                throw new IllegalArgumentException("缺少必选位置参数 <" + p.name() + ">");
            }
        }
        for (ArgSpec o : optionals) {
            if (o.required() && !out.contains(o.name())) {
                throw new IllegalArgumentException("缺少必选选项 --" + o.name());
            }
        }
        validateCombinations(out);
        return out;
    }

    /**
     * 校验并归一化一个结构化参数 Map（Agent JSON 等）。
     *
     * @param map 参数名 → 值
     * @return 归一化后的解析结果
     * @throws IllegalArgumentException 参数非法或冲突时
     */
    public ParsedArgs validate(Map<String, Object> map) {
        ParsedArgs out = new ParsedArgs();
        for (ArgSpec spec : optionals) {
            Object v = map.get(spec.name());
            if (v == null) {
                if (spec.required()) {
                    throw new IllegalArgumentException("缺少必选参数: " + spec.name());
                }
                if (spec.defaultValue() != null) {
                    out.put(spec.name(), convert(spec, spec.defaultValue()));
                }
                continue;
            }
            out.put(spec.name(), convert(spec, v));
        }
        for (ArgSpec pos : positionals) {
            Object v = map.get(pos.name());
            if (v == null) {
                if (pos.required()) {
                    throw new IllegalArgumentException("缺少必选参数: " + pos.name());
                }
                continue;
            }
            out.put(pos.name(), convert(pos, v));
        }
        validateCombinations(out);
        return out;
    }

    private int consumeOption(ArgSpec spec, List<String> raw, int i, ParsedArgs out) {
        if (spec.type() == ArgSpec.Type.BOOLEAN) {
            out.put(spec.name(), true);
            return i + 1;
        }
        if (i + 1 >= raw.size()) {
            throw new IllegalArgumentException("选项 --" + spec.name() + " 缺少值");
        }
        String value = raw.get(i + 1);
        if (isOption(value)) {
            throw new IllegalArgumentException("选项 --" + spec.name() + " 缺少值");
        }
        if (spec.type() == ArgSpec.Type.STRING_LIST) {
            out.mutableList(spec.name()).add(value);
        } else {
            out.put(spec.name(), convert(spec, value));
        }
        return i + 2;
    }

    private Object convert(ArgSpec spec, Object rawValue) {
        String s = String.valueOf(rawValue);
        return switch (spec.type()) {
            case BOOLEAN -> parseBoolean(s, spec);
            case INT -> parseInt(s, spec);
            case STRING -> validateEnum(s, spec);
            case STRING_LIST -> {
                if (rawValue instanceof List<?> list) {
                    List<String> converted = new ArrayList<>(list.size());
                    for (Object o : list) {
                        converted.add(validateEnum(String.valueOf(o), spec));
                    }
                    yield converted;
                }
                yield validateEnum(s, spec);
            }
        };
    }

    private boolean parseBoolean(String s, ArgSpec spec) {
        if ("true".equalsIgnoreCase(s) || "1".equals(s)) {
            return true;
        }
        if ("false".equalsIgnoreCase(s) || "0".equals(s)) {
            return false;
        }
        throw new IllegalArgumentException("选项 --" + spec.name() + " 期望布尔值，实际: " + s);
    }

    private int parseInt(String s, ArgSpec spec) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("选项 --" + spec.name() + " 期望整数，实际: " + s);
        }
    }

    private String validateEnum(String s, ArgSpec spec) {
        if (!spec.allowedValues().isEmpty() && !spec.allowedValues().contains(s)) {
            throw new IllegalArgumentException("参数 " + spec.name() + " 取值非法: " + s
                    + "，允许值: " + spec.allowedValues());
        }
        return s;
    }

    private boolean isOption(String token) {
        if (token.length() < 2) {
            return false;
        }
        if (token.startsWith("--")) {
            return true;
        }
        // 单短横杠：仅当为 "-x" 形态（一个字母）时视为选项；"-5"、"abc" 等视为普通值
        return token.charAt(0) == '-' && token.length() == 2 && Character.isLetter(token.charAt(1));
    }

    private String stripOption(String token) {
        return token.startsWith("--") ? token.substring(2) : token.substring(1);
    }

    private void validateCombinations(ParsedArgs out) {
        for (List<String> group : mutexGroups) {
            List<String> present = group.stream().filter(out::contains).toList();
            if (present.size() > 1) {
                throw new IllegalArgumentException("参数互斥，不能同时指定: " + present);
            }
        }
        for (List<String> group : oneOfGroups) {
            List<String> present = group.stream().filter(out::contains).toList();
            if (present.isEmpty()) {
                throw new IllegalArgumentException("以下参数必须指定其一: " + group);
            }
        }
    }
}
