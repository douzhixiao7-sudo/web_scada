package com.example.scada.device;

public record DeviceResponse(
        Long id,
        Long areaId,
        String areaName,
        String name,
        String code,
        String type,
        String status,
        String protocol,
        String ipAddress,
        Integer port,
        String description,
        Integer pointCount) {
}
