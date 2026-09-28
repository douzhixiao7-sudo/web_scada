package com.example.scada.alarm;

public record AlarmRuleRequest(
        Long pointId,
        String ruleName,
        String ruleType,
        String operator,
        Double thresholdValue,
        String level,
        String message,
        Boolean enabled) {
}
