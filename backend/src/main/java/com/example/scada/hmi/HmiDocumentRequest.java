package com.example.scada.hmi;

import tools.jackson.databind.JsonNode;

public record HmiDocumentRequest(JsonNode document, Long expectedDraftVersion) {
}
