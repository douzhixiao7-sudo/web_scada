package com.example.scada.hmi;

import java.time.Instant;

public record HmiScreenResponse(Long id, String code, String name, boolean enabled, int sortOrder, boolean defaultScreen, long draftVersion,
        Long publishedRevisionId, Integer publishedVersion, String updatedBy, Instant updatedAt) {
}
