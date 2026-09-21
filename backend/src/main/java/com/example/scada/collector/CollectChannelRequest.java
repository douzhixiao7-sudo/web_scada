package com.example.scada.collector;

public record CollectChannelRequest(
        String name,
        String protocol,
        String channelMode,
        String host,
        Integer port,
        Integer slaveId,
        Integer timeoutMs,
        Integer retryCount,
        Integer pollIntervalMs,
        Boolean enabled,
        String status) {
}
