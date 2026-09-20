package com.example.scada.alarm;

import java.time.Instant;

public record AlarmEventResponse(
        Long id,
        String alarmKey,
        Long deviceId,
        String deviceName,
        Long pointId,
        String pointCode,
        String pointName,
        String level,
        String message,
        String value,
        String quality,
        String status,
        Instant occurredAt,
        Instant lastSeenAt,
        Instant recoveredAt,
        Instant acknowledgedAt,
        String acknowledgedBy,
        String ackNote) {
}
