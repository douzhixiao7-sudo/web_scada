package com.example.scada.device;

public record PointRequest(
        String name,
        String code,
        String dataType,
        String unit,
        String address,
        String accessMode,
        Double scaleValue,
        Integer sortOrder) {
}
