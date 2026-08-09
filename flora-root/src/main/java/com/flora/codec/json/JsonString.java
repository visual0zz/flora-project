package com.flora.codec.json;

/**
 * JSON 字符串值，包裹 {@link String}。
 */
public final class JsonString implements JsonValue {

    private final String value;

    public JsonString(String value) {
        this.value = value;
    }

    /** 取得包裹的字符串。 */
    public String value() {
        return value;
    }

    @Override
    public boolean isString() {
        return true;
    }

    @Override
    public String asString() {
        return value;
    }

    @Override
    public Object toNative() {
        return value;
    }

    @Override
    public String toJsonString() {
        return JsonBuilder.toJsonString(value);
    }

    @Override
    public String toPrettyString() {
        return JsonBuilder.toPrettyJsonString(value);
    }

    @Override
    public String typeName() {
        return "string";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonString && value.equals(((JsonString) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
