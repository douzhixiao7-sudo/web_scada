package com.example.scada.alarm;

import java.time.Instant;

public record AlarmEventResponse(
        String id,
        Long deviceId,
        String deviceName,
        Long pointId,
        String pointCode,
        String pointName,
        String level,
        String message,
        String value,
        String quality,
        Instant occurredAt) {
}
