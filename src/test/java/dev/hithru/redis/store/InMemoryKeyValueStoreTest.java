package dev.hithru.redis.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryKeyValueStoreTest {

    @Test
    void setAndGetWithoutExpiry() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        long now = System.currentTimeMillis();

        store.set("foo", "bar", null);

        assertEquals("bar", store.get("foo", now));
        assertTrue(store.exists("foo", now));
    }

    @Test
    void expiredKeyIsRemovedOnAccess() throws InterruptedException {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        long now = System.currentTimeMillis();

        store.set("foo", "bar", now + 50);

        assertEquals("bar", store.get("foo", now));

        Thread.sleep(60);

        long later = System.currentTimeMillis();
        assertNull(store.get("foo", later));
        assertFalse(store.exists("foo", later));
    }

    @Test
    void deleteRemovesKey() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        long now = System.currentTimeMillis();

        store.set("foo", "bar", null);
        assertEquals("bar", store.get("foo", now));

        store.delete("foo");
        assertNull(store.get("foo", now));
    }
}
