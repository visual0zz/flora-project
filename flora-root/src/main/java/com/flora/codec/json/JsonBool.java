package com.flora.codec.json;

/**
 * JSON 布尔值，包裹 {@code boolean}。
 */
public final class JsonBool implements JsonValue {

    private final boolean value;

    public JsonBool(boolean value) {
        this.value = value;
    }

    /** 取得包裹的布尔值。 */
    public boolean value() {
        return value;
    }

    @Override
    public boolean isBool() {
        return true;
    }

    @Override
    public boolean asBool() {
        return value;
    }

    @Override
    public Object toNative() {
        return value;
    }

    @Override
    public String toJsonString() {
        return Boolean.toString(value);
    }

    @Override
    public String toPrettyString() {
        return Boolean.toString(value);
    }

    @Override
    public String typeName() {
        return "boolean";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonBool && value == ((JsonBool) o).value;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(value);
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
