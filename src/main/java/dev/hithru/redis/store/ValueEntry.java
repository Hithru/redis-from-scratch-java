package dev.hithru.redis.store;
public class ValueEntry {
    private final String value;
    private final Long expireAtMs; // null means no expiry

    public ValueEntry(String value, Long expireAtMs) {
        this.value = value;
        this.expireAtMs = expireAtMs;
    }

    public String getValue() {
        return value;
    }

    public Long getExpireAtMs() {
        return expireAtMs;
    }

    public boolean isExpired(long nowMs) {
        return expireAtMs != null && nowMs >= expireAtMs;
    }
}
