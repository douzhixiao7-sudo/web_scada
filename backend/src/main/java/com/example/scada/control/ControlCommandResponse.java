package com.example.scada.control;

import java.math.BigDecimal;
import java.time.Instant;

public record ControlCommandResponse(
        Long id,
        String commandNo,
        Long deviceId,
        String deviceName,
        Long pointId,
        String pointCode,
        String pointName,
        BigDecimal targetValue,
        String status,
        String message,
        String requestedBy,
        Instant createdAt,
        Instant executedAt) {
}
