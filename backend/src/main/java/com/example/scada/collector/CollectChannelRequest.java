package com.example.scada.collector;

public record CollectChannelRequest(
        String name,
        String protocol,
        String host,
        Integer port,
        Integer pollIntervalMs,
        Boolean enabled,
        String status) {
}
