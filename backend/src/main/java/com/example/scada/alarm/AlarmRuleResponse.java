package com.example.scada.alarm;

public record AlarmRuleResponse(
        Long id,
        Long pointId,
        String pointCode,
        String pointName,
        String ruleName,
        String ruleType,
        String operator,
        Double thresholdValue,
        String level,
        String message,
        Boolean enabled) {
}
