package com.example.scada.realtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimeValueCache {
    private static final String VALUE_HASH_KEY = "scada:realtime:values";
    private static final String SEP = "\u001F";

    private final StringRedisTemplate redisTemplate;

    public RealtimeValueCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(RealtimeValueResponse value) {
        if (value == null || value.pointId() == null) {
            return;
        }
        redisTemplate.opsForHash().put(VALUE_HASH_KEY, String.valueOf(value.pointId()), serialize(value));
    }

    public void putAll(List<RealtimeValueResponse> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Map<String, String> payload = new LinkedHashMap<>();
        for (RealtimeValueResponse value : values) {
            if (value == null || value.pointId() == null) {
                continue;
            }
            payload.put(String.valueOf(value.pointId()), serialize(value));
        }
        if (!payload.isEmpty()) {
            redisTemplate.opsForHash().putAll(VALUE_HASH_KEY, payload);
        }
    }

    public Map<Long, RealtimeValueResponse> getAll(List<Long> pointIds) {
        Map<Long, RealtimeValueResponse> values = new LinkedHashMap<>();
        if (pointIds == null || pointIds.isEmpty()) {
            return values;
        }
        List<Object> keys = pointIds.stream().filter(Objects::nonNull).map(String::valueOf).map(value -> (Object) value).toList();
        List<Object> rawValues = redisTemplate.opsForHash().multiGet(VALUE_HASH_KEY, keys);
        if (rawValues == null) {
            return values;
        }
        for (int i = 0; i < keys.size(); i++) {
            Object raw = i < rawValues.size() ? rawValues.get(i) : null;
            if (raw == null) {
                continue;
            }
            RealtimeValueResponse value = parse(raw.toString());
            if (value != null) {
                values.put(value.pointId(), value);
            }
        }
        return values;
    }

    public long size() {
        Long size = redisTemplate.opsForHash().size(VALUE_HASH_KEY);
        return size == null ? 0 : size;
    }

    public RealtimeValueResponse stale(Long pointId, Long deviceId, String pointCode) {
        return new RealtimeValueResponse(pointId, deviceId, pointCode, "", "STALE", Instant.now());
    }

    private String serialize(RealtimeValueResponse value) {
        return value.pointId() + SEP
                + value.deviceId() + SEP
                + clean(value.pointCode()) + SEP
                + clean(value.value()) + SEP
                + clean(value.quality()) + SEP
                + value.collectedAt().toString();
    }

    private RealtimeValueResponse parse(String payload) {
        String[] parts = payload.split(SEP, -1);
        if (parts.length != 6) {
            return null;
        }
        try {
            return new RealtimeValueResponse(
                    Long.parseLong(parts[0]),
                    Long.parseLong(parts[1]),
                    parts[2],
                    parts[3],
                    parts[4],
                    Instant.parse(parts[5])
            );
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.replace(SEP, " ");
    }
}
