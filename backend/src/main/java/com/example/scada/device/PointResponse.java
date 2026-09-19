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
        Integer sortOrder,
        String sourceGroup,
        String sourceSheet,
        String ioModule,
        String ioType,
        String modbusType,
        String sixnetAddress,
        String iconicsPath,
        String remark) {
}
