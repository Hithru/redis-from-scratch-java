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

    /**
     * LPUSH key value... -> returns new length
     * Values are prepended so that:
     *   LPUSH key a b c
     * results in list [c, b, a].
     */
    public int lpush(String key, List<String> values) {
        List<String> list = lists.computeIfAbsent(key, k -> new ArrayList<>());
        for (String value : values) {
            list.add(0, value);
        }
        return list.size();
    }

    /**
     * LRANGE key start stop with support for negative indexes.
     *
     * Rules:
     *  - Missing key -> empty list
     *  - Negative index = offset from end (-1 = last, -2 = second last, etc.)
     *  - Negative index out of range (e.g. -6 on len 5) is treated as 0
     *  - start >= len -> empty
     *  - stop >= len -> clamp to len - 1
     *  - start > stop -> empty
     */
    public List<String> lrange(String key, int start, int stop) {
        List<String> list = lists.get(key);
        if (list == null) {
            return Collections.emptyList();
        }

        int size = list.size();
        if (size == 0) {
            return Collections.emptyList();
        }

        // Convert negative indexes to positive offsets from end
        if (start < 0) {
            start = size + start;
        }
        if (stop < 0) {
            stop = size + stop;
        }

        // Out-of-range negative indexes become 0 
        if (start < 0) {
            start = 0;
        }
        if (stop < 0) {
            stop = 0;
        }

        // If start is past the end => empty
        if (start >= size) {
            return Collections.emptyList();
        }

        // Clamp stop to last index
        if (stop >= size) {
            stop = size - 1;
        }

        // If range is inverted => empty
        if (start > stop) {
            return Collections.emptyList();
        }

        // Return a copy of the slice [start, stop] inclusive
        return new ArrayList<>(list.subList(start, stop + 1));
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
