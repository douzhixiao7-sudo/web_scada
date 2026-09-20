package com.example.scada.collector;

public record CollectBindingResponse(
        Long id,
        Long channelId,
        Long pointId,
        String pointCode,
        String pointName,
        String address,
        String modbusType,
        String accessMode,
        Boolean enabled) {
}
