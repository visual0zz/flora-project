package com.flora.java.converter;

import org.junit.jupiter.api.Test;

import com.flora.java.ConvertUtil;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ByteArrayConverterTest {

    private final ByteArrayConverter converter = new ByteArrayConverter();

    @Test
    void nullInputReturnsNull() {
        assertNull(converter.convert(null, String.class));
        assertNull(converter.convert(null, byte[].class));
    }

    @Test
    void bytesToStringHexLowercase() {
        byte[] data = {0x01, 0x0a, (byte) 0xff};
        assertEquals("010aff", converter.convert(data, String.class));
    }

    @Test
    void stringToBytesHex() {
        byte[] expected = {0x01, 0x0a, (byte) 0xff};
        assertArrayEquals(expected, (byte[]) converter.convert("010aff", byte[].class));
    }

    @Test
    void stringToBytesUpperCaseHex() {
        byte[] expected = {0x01, 0x0a};
        assertArrayEquals(expected, (byte[]) converter.convert("010A", byte[].class));
    }

    @Test
    void roundTripViaFacade() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        String hex = ConvertUtil.convert(String.class, data);
        assertArrayEquals(data, (byte[]) ConvertUtil.convert(byte[].class, hex));
    }

    @Test
    void invalidHexThrows() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("zz", byte[].class));
    }

    @Test
    void byteArrayToStringViaFacadeHitsByteArrayConverterNotUuidBytes() {
        // byte[] -> String 必须产出 hex（命中 ByteArrayConverter），而非被 UuidBytesConverter 当成 UUID 字节
        byte[] data = {0x01, 0x02, 0x03};
        assertEquals("010203", ConvertUtil.convert(String.class, data));
    }
}
