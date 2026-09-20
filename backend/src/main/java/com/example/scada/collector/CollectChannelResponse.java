package com.example.scada.collector;

public record CollectChannelResponse(
        Long id,
        Long deviceId,
        String deviceName,
        String deviceCode,
        String name,
        String code,
        String protocol,
        String host,
        Integer port,
        Integer pollIntervalMs,
        Boolean enabled,
        String status,
        Integer pointCount,
        String lastPolledAt) {
}
