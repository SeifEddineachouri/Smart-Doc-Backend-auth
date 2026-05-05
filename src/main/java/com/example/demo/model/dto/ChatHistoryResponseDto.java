package com.example.demo.model.dto;

import java.util.List;
import java.util.UUID;

public record ChatHistoryResponseDto(
    List<ChatHistoryItemDto> items,
    int page,
    int size,
    long totalItems,
    int totalPages,
    UUID sessionId
) {
}

