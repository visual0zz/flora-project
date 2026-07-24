package com.flora.sanctum.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EntryTest {

    @Test
    void entryCreatedWithIdAndTimestamps() {
        Entry entry = new Entry();
        assertNotNull(entry.getId());
        assertTrue(entry.getCreatedAt() > 0);
        assertTrue(entry.getUpdatedAt() > 0);
        assertEquals(entry.getCreatedAt(), entry.getUpdatedAt());
    }

    @Test
    void entryToMapRoundTrip() {
        Entry entry = new Entry("My Title", "user1", "pass123",
                "https://example.com", "Work", "A note");
        entry.setId("test-id-1");

        Map<String, Object> map = entry.toMap();
        Entry restored = Entry.fromMap(map);

        assertEquals(entry.getId(), restored.getId());
        assertEquals(entry.getTitle(), restored.getTitle());
        assertEquals(entry.getUsername(), restored.getUsername());
        assertEquals(entry.getPassword(), restored.getPassword());
        assertEquals(entry.getUrl(), restored.getUrl());
        assertEquals(entry.getCategory(), restored.getCategory());
        assertEquals(entry.getNote(), restored.getNote());
        assertEquals(entry.getCreatedAt(), restored.getCreatedAt());
        assertEquals(entry.getUpdatedAt(), restored.getUpdatedAt());
    }

    @Test
    void touchUpdatesTimestamp() {
        Entry entry = new Entry();
        long original = entry.getUpdatedAt();
        entry.touch();
        assertTrue(entry.getUpdatedAt() >= original);
    }

    @Test
    void equalsById() {
        Entry a = new Entry();
        a.setId("same-id");
        Entry b = new Entry();
        b.setId("same-id");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
