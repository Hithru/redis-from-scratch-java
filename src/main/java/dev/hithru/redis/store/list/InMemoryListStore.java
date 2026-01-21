package dev.hithru.redis.store.list;

import java.util.*;

/**
 * Simple in-memory store for Redis-style lists.
 * For now:
 *  - RPUSH appends elements to the right
 *  - If the list does not exist, it's created
 */
public class InMemoryListStore {

    private final Map<String, List<String>> lists = new HashMap<>();

    /**
     * RPUSH key value... -> returns new length
     */
    public int rpush(String key, List<String> values) {
        List<String> list = lists.computeIfAbsent(key, k -> new ArrayList<>());
        list.addAll(values);
        return list.size();
    }

    public List<String> getList(String key) {
        return lists.get(key);
    }

    public boolean exists(String key) {
        return lists.containsKey(key);
    }

    public int size(String key) {
        List<String> list = lists.get(key);
        return list == null ? 0 : list.size();
    }
}
