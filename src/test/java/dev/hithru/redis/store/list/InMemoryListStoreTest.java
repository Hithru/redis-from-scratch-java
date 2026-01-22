package dev.hithru.redis.store.list;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryListStoreTest {

    @Test
    void rpushCreatesNewListWithSingleElement() {
        InMemoryListStore store = new InMemoryListStore();

        int len = store.rpush("mylist", List.of("foo"));

        assertEquals(1, len);
        assertEquals(List.of("foo"), store.getList("mylist"));
    }

    @Test
    void rpushAppendsToExistingList() {
        InMemoryListStore store = new InMemoryListStore();

        store.rpush("mylist", List.of("foo"));
        int len = store.rpush("mylist", List.of("bar", "baz"));

        assertEquals(3, len);
        assertEquals(List.of("foo", "bar", "baz"), store.getList("mylist"));
    }
}
