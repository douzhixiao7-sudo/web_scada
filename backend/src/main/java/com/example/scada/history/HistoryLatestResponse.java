package com.example.scada.history;

public record HistoryLatestResponse(
        Long pointId,
        String pointCode,
        String pointName,
        String value,
        String quality,
        String collectedAt) {
}
