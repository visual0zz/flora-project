package com.flora.root.java.converter;

import org.junit.jupiter.api.Test;

import com.flora.root.java.ConvertUtil;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UuidBytesConverterTest {

    private final UuidBytesConverter converter = new UuidBytesConverter();
    private final UUID sample = UUID.fromString("12345678-1234-1234-1234-123456789012");

    @Test
    void nullInputReturnsNull() {
        assertNull(converter.convert(null, UUID.class));
        assertNull(converter.convert(null, byte[].class));
    }

    @Test
    void uuidToBytesRfc4122BigEndian() {
        byte[] expected = {
                0x12, 0x34, 0x56, 0x78, 0x12, 0x34, 0x12, 0x34,
                0x12, 0x34, 0x12, 0x34, 0x56, 0x78, (byte) 0x90, 0x12
        };
        assertArrayEquals(expected, (byte[]) converter.convert(sample, byte[].class));
    }

    @Test
    void zeroUuidToAllZeroBytes() {
        byte[] expected = new byte[16];
        assertArrayEquals(expected, (byte[]) converter.convert(new UUID(0, 0), byte[].class));
    }

    @Test
    void maxUuidToAllFFBytes() {
        byte[] expected = new byte[16];
        java.util.Arrays.fill(expected, (byte) 0xff);
        assertArrayEquals(expected, (byte[]) converter.convert(new UUID(-1L, -1L), byte[].class));
    }

    @Test
    void bytesToUuidRoundTrip() {
        byte[] bytes = (byte[]) converter.convert(sample, byte[].class);
        assertEquals(sample, converter.convert(bytes, UUID.class));
    }

    @Test
    void invalidLengthThrows() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert(new byte[8], UUID.class));
    }

    @Test
    void uuidToBytesViaFacade() {
        byte[] expected = {
                0x12, 0x34, 0x56, 0x78, 0x12, 0x34, 0x12, 0x34,
                0x12, 0x34, 0x12, 0x34, 0x56, 0x78, (byte) 0x90, 0x12
        };
        assertArrayEquals(expected, (byte[]) ConvertUtil.convert(byte[].class, sample));
    }

    @Test
    void bytesToUuidViaFacade() {
        byte[] bytes = {
                0x12, 0x34, 0x56, 0x78, 0x12, 0x34, 0x12, 0x34,
                0x12, 0x34, 0x12, 0x34, 0x56, 0x78, (byte) 0x90, 0x12
        };
        assertEquals(sample, ConvertUtil.convert(UUID.class, bytes));
    }

    @Test
    void byteArrayToUuidViaFacadeHitsUuidBytesNotByteArrayConverter() {
        // byte[] -> UUID 必须产出正确 UUID（命中 UuidBytesConverter），而非被 ByteArrayConverter 当 hex 解码后抛错
        byte[] bytes = {
                0x12, 0x34, 0x56, 0x78, 0x12, 0x34, 0x12, 0x34,
                0x12, 0x34, 0x12, 0x34, 0x56, 0x78, (byte) 0x90, 0x12
        };
        assertEquals(sample, ConvertUtil.convert(UUID.class, bytes));
    }
}
