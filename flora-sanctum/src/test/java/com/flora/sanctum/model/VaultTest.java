package com.flora.sanctum.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VaultTest {

    @Test
    void putEntryAddsNewEntry() {
        Vault vault = new Vault();
        Entry entry = new Entry("Test", "u", "p", null, null, null);
        vault.putEntry(entry);
        assertEquals(1, vault.getEntries().size());
        assertSame(entry, vault.findEntryById(entry.getId()));
    }

    @Test
    void putEntryUpdatesExisting() {
        Vault vault = new Vault();
        Entry entry = new Entry("Original", "u", "p", null, null, null);
        vault.putEntry(entry);

        Entry updated = new Entry("Updated", "u2", "p2", null, null, null);
        updated.setId(entry.getId());
        vault.putEntry(updated);

        assertEquals(1, vault.getEntries().size());
        assertEquals("Updated", vault.getEntries().get(0).getTitle());
    }

    @Test
    void removeEntry() {
        Vault vault = new Vault();
        Entry entry = new Entry("Test", "u", "p", null, null, null);
        vault.putEntry(entry);
        assertTrue(vault.removeEntry(entry.getId()));
        assertNull(vault.findEntryById(entry.getId()));
        assertFalse(vault.removeEntry("nonexistent"));
    }
}
