package com.example.scada.history;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.scada.realtime.RealtimeValueResponse;

@Component
public class HistorySampleWriter {
    private static final Duration ANALOG_SAMPLE_INTERVAL = Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;
    private final Map<Long, LastSample> lastSamples = new ConcurrentHashMap<>();

    public HistorySampleWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void writeSamples(List<HistoryCandidate> candidates) {
        List<HistoryCandidate> writable = new ArrayList<>();
        for (HistoryCandidate candidate : candidates) {
            if (shouldWrite(candidate)) {
                writable.add(candidate);
                lastSamples.put(candidate.value().pointId(), new LastSample(candidate.value().value(), candidate.value().quality(), candidate.value().collectedAt()));
            }
        }
        if (writable.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                insert into scada_history_value(device_id, point_id, point_code, point_name, value, quality, collected_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                HistoryCandidate candidate = writable.get(i);
                RealtimeValueResponse value = candidate.value();
                ps.setLong(1, value.deviceId());
                ps.setLong(2, value.pointId());
                ps.setString(3, value.pointCode());
                ps.setString(4, candidate.pointName());
                ps.setString(5, value.value());
                ps.setString(6, value.quality());
                ps.setTimestamp(7, Timestamp.from(value.collectedAt()));
            }

            @Override
            public int getBatchSize() {
                return writable.size();
            }
        });
    }

    private boolean shouldWrite(HistoryCandidate candidate) {
        RealtimeValueResponse current = candidate.value();
        LastSample previous = lastSamples.get(current.pointId());
        if (!"GOOD".equalsIgnoreCase(current.quality())) {
            return previous == null || !current.quality().equals(previous.quality()) || !current.value().equals(previous.value());
        }
        if (previous == null) {
            return true;
        }
        if (isDigital(candidate)) {
            return !current.value().equals(previous.value());
        }
        return Duration.between(previous.collectedAt(), current.collectedAt()).compareTo(ANALOG_SAMPLE_INTERVAL) >= 0;
    }

    private boolean isDigital(HistoryCandidate candidate) {
        String dataType = candidate.dataType() == null ? "" : candidate.dataType();
        String ioType = candidate.ioType() == null ? "" : candidate.ioType().toUpperCase(java.util.Locale.ROOT);
        String pointCode = candidate.value().pointCode() == null ? "" : candidate.value().pointCode().toUpperCase(java.util.Locale.ROOT);
        return "BOOLEAN".equalsIgnoreCase(dataType) || ioType.startsWith("DI") || ioType.startsWith("DO") || pointCode.startsWith("DI_") || pointCode.startsWith("DO_");
    }

    public record HistoryCandidate(String pointName, String dataType, String ioType, RealtimeValueResponse value) {
    }

    private record LastSample(String value, String quality, Instant collectedAt) {
    }
}
