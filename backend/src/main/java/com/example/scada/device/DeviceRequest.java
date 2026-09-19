package com.example.scada.device;

public record DeviceRequest(
        Long areaId,
        String name,
        String code,
        String type,
        String status,
        String protocol,
        String ipAddress,
        Integer port,
        String description) {
}
