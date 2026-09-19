package com.example.scada.device;

public record PointResponse(
        Long id,
        Long deviceId,
        String name,
        String code,
        String dataType,
        String unit,
        String address,
        String accessMode,
        Double scaleValue,
        Integer sortOrder) {
}
