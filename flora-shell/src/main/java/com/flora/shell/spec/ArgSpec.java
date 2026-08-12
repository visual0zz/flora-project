package com.flora.shell.spec;

import com.flora.java.CheckUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数声明。声明式描述一个命令接受的一个参数项：选项或位置参数。
 * <p>解析、help 生成、Agent 的 JSON schema 都从这份声明推导，保证"一处定义、处处一致"。
 * 本类是不可变值对象，通过 {@link #builder()} 构建。</p>
 */
public final class ArgSpec {

    /** 参数种类：选项（{@code --name} / {@code -n}）或位置参数（{@code <src>}）。 */
    public enum Kind {
        OPTION, POSITIONAL
    }

    /** 值类型。 */
    public enum Type {
        BOOLEAN, INT, STRING, STRING_LIST
    }

    private final Kind kind;
    private final String name;
    private final String shortName;
    private final String description;
    private final Type type;
    private final boolean required;
    private final boolean variadic;
    private final Object defaultValue;
    private final List<String> allowedValues;

    private ArgSpec(Builder b) {
        this.kind = b.kind;
        this.name = CheckUtil.notBlank(b.name, "参数名不能为空");
        this.shortName = b.shortName;
        this.description = b.description == null ? "" : b.description;
        this.type = b.type == null ? Type.STRING : b.type;
        this.required = b.required;
        this.variadic = b.variadic;
        this.defaultValue = b.defaultValue;
        this.allowedValues = b.allowedValues == null ? List.of() : List.copyOf(b.allowedValues);
        CheckUtil.mustTrue(!(required && defaultValue != null),
                "参数 '" + name + "' 不能同时必选且有默认值");
    }

    /**
     * @return 参数种类
     */
    public Kind kind() {
        return kind;
    }

    /**
     * @return 参数名（选项为长名，如 {@code port}；位置参数为位置标识）
     */
    public String name() {
        return name;
    }

    /**
     * @return 选项短名（如 {@code p}）；位置参数为 {@code null}
     */
    public String shortName() {
        return shortName;
    }

    /**
     * @return 一句话说明
     */
    public String description() {
        return description;
    }

    /**
     * @return 值类型
     */
    public Type type() {
        return type;
    }

    /**
     * @return 是否必选
     */
    public boolean required() {
        return required;
    }

    /**
     * @return 是否变长（位置参数收集到列表，仅 STRING_LIST 有效）
     */
    public boolean variadic() {
        return variadic;
    }

    /**
     * @return 默认值；可为 {@code null}
     */
    public Object defaultValue() {
        return defaultValue;
    }

    /**
     * @return 合法值枚举；空表示不限制
     */
    public List<String> allowedValues() {
        return allowedValues;
    }

    /**
     * @return 该参数的帮助行（如 {@code -p, --port <int>  服务端口}）
     */
    public String helpLine() {
        StringBuilder sb = new StringBuilder();
        if (kind == Kind.OPTION) {
            if (shortName != null) {
                sb.append('-').append(shortName).append(", ");
            }
            sb.append("--").append(name);
        } else {
            sb.append('<').append(name).append('>');
        }
        if (type != Type.BOOLEAN && kind == Kind.OPTION) {
            sb.append(" <").append(type.name().toLowerCase()).append('>');
        }
        if (variadic) {
            sb.append("...");
        }
        if (required) {
            sb.append(" [必选]");
        }
        sb.append("  ").append(description);
        return sb.toString();
    }

    /**
     * @return 新建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /** ArgSpec 构建器。 */
    public static final class Builder {
        private Kind kind = Kind.OPTION;
        private String name;
        private String shortName;
        private String description;
        private Type type = Type.STRING;
        private boolean required;
        private boolean variadic;
        private Object defaultValue;
        private List<String> allowedValues = new ArrayList<>();

        private Builder() {
        }

        /**
         * @param kind 参数种类
         * @return this
         */
        public Builder kind(Kind kind) {
            this.kind = kind;
            return this;
        }

        /**
         * @param name 参数名
         * @return this
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * @param shortName 选项短名
         * @return this
         */
        public Builder shortName(String shortName) {
            this.shortName = shortName;
            return this;
        }

        /**
         * @param description 一句话说明
         * @return this
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * @param type 值类型
         * @return this
         */
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        /**
         * @param required 是否必选
         * @return this
         */
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * @param variadic 是否变长
         * @return this
         */
        public Builder variadic(boolean variadic) {
            this.variadic = variadic;
            return this;
        }

        /**
         * @param defaultValue 默认值
         * @return this
         */
        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * @param allowed 合法值枚举
         * @return this
         */
        public Builder allowedValues(String... allowed) {
            this.allowedValues = List.of(allowed);
            return this;
        }

        /**
         * @return 构建完成的 ArgSpec
         */
        public ArgSpec build() {
            return new ArgSpec(this);
        }
    }
}
