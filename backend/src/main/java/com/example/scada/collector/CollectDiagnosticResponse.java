package com.example.scada.collector;

public record CollectDiagnosticResponse(
        Long channelId,
        String channelName,
        Boolean success,
        String message,
        Long latencyMs,
        Long pointId,
        String pointCode,
        String pointName,
        String rawValue,
        String quality) {
}
