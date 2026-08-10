package com.flora.codec.json.model;

/**
 * JSON null 值。单例 {@link #INSTANCE} 复用，无状态。
 */
public final class JsonNull implements JsonValue {

    /** 全局唯一实例。 */
    public static final JsonNull INSTANCE = new JsonNull();

    private JsonNull() {
    }

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public Object toNative() {
        return null;
    }

    @Override
    public String toJsonString() {
        return "null";
    }

    @Override
    public String toPrettyString() {
        return "null";
    }

    @Override
    public String typeName() {
        return "null";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonNull;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public String toString() {
        return "null";
    }
}
