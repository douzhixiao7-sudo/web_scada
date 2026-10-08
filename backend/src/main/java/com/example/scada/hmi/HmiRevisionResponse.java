package com.example.scada.hmi;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

public record HmiRevisionResponse(Long id, int version, JsonNode document, String publishedBy,
        Instant createdAt, boolean current) {
}
