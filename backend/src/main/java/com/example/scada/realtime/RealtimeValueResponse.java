package com.example.scada.realtime;

import java.time.Instant;

public record RealtimeValueResponse(
        Long pointId,
        Long deviceId,
        String pointCode,
        String value,
        String quality,
        Instant collectedAt) {
}
