package com.example.scada.control;

import java.math.BigDecimal;

public record ControlCommandRequest(Long pointId, BigDecimal targetValue) {
}
