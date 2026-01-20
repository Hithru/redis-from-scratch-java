package dev.hithru.redis.store;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple in-memory KV store with passive expiry.
 * This can later be extended/replaced when you add lists, sets, etc.
 */
public class InMemoryKeyValueStore {

    private final Map<String, ValueEntry> store = new HashMap<>();

    public void set(String key, String value, Long expireAtMs) {
        store.put(key, new ValueEntry(value, expireAtMs));
    }

    public ValueEntry getRaw(String key) {
        return store.get(key);
    }

    public String get(String key, long nowMs) {
        ValueEntry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(nowMs)) {
            store.remove(key);
            return null;
        }
        return entry.getValue();
    }

    public boolean exists(String key, long nowMs) {
        return get(key, nowMs) != null;
    }

    public void delete(String key) {
        store.remove(key);
    }

    public int size() {
        return store.size();
    }
}
