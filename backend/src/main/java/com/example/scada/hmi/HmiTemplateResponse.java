package com.example.scada.hmi;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record HmiTemplateResponse(long id, String name, JsonNode document, String updatedBy, Instant updatedAt) {
}
