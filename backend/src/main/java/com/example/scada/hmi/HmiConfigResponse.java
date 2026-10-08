package com.example.scada.hmi;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

public record HmiConfigResponse(JsonNode document, long draftVersion, Long publishedRevisionId,
        Integer publishedVersion, String updatedBy, Instant updatedAt) {
}
