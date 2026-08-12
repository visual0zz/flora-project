package com.flora.root.java.converter;

import org.junit.jupiter.api.Test;

import com.flora.root.java.ConvertUtil;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UuidStringConverterTest {

    private final UuidStringConverter converter = new UuidStringConverter();
    private final UUID sample = UUID.fromString("12345678-1234-1234-1234-123456789012");

    @Test
    void nullInputReturnsNull() {
        assertNull(converter.convert(null, UUID.class));
        assertNull(converter.convert(null, String.class));
    }

    @Test
    void uuidToString() {
        assertEquals("12345678-1234-1234-1234-123456789012", converter.convert(sample, String.class));
    }

    @Test
    void stringToUuid() {
        assertEquals(sample, converter.convert("12345678-1234-1234-1234-123456789012", UUID.class));
    }

    @Test
    void stringToUuidTrimsWhitespace() {
        assertEquals(sample, converter.convert("  12345678-1234-1234-1234-123456789012  ", UUID.class));
    }

    @Test
    void invalidUuidStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("not-a-uuid", UUID.class));
    }

    @Test
    void uuidToStringViaFacade() {
        assertEquals("12345678-1234-1234-1234-123456789012",
                ConvertUtil.convert(String.class, sample));
    }

    @Test
    void stringToUuidViaFacade() {
        assertEquals(sample, ConvertUtil.convert(UUID.class, "12345678-1234-1234-1234-123456789012"));
    }
}
