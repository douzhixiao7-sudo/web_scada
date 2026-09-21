package com.example.scada.history;

import java.time.Instant;

public record HistoryValueResponse(
        Long id,
        Long deviceId,
        Long pointId,
        String pointCode,
        String pointName,
        String value,
        String quality,
        Instant collectedAt) {
}
