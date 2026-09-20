package com.example.scada.alarm;

public record AlarmRuleRequest(
        String ruleName,
        String operator,
        Double thresholdValue,
        String level,
        String message,
        Boolean enabled) {
}
