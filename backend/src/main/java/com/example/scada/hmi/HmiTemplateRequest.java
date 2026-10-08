package com.example.scada.hmi;

import tools.jackson.databind.JsonNode;

public record HmiTemplateRequest(String name, JsonNode document) {
}
