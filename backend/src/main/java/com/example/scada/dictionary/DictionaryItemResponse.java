package com.example.scada.dictionary;

public record DictionaryItemResponse(
        String typeCode,
        String itemCode,
        String label,
        String description,
        int sortOrder) {
}
