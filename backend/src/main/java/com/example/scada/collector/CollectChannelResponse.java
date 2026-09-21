package com.example.scada.collector;

public record CollectChannelResponse(
        Long id,
        Long deviceId,
        String deviceName,
        String deviceCode,
        String name,
        String code,
        String protocol,
        String channelMode,
        String host,
        Integer port,
        Integer slaveId,
        Integer timeoutMs,
        Integer retryCount,
        Integer pollIntervalMs,
        Boolean enabled,
        String status,
        Integer pointCount,
        String lastPolledAt,
        String lastSuccessAt,
        String lastError,
        Integer lastLatencyMs,
        Integer consecutiveFailures) {
}
