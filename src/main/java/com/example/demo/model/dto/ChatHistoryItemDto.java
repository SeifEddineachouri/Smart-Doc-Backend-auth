package com.example.demo.model.dto;

import java.time.Instant;

public record ChatHistoryItemDto(
    Long id,
    String question,
    String answer,
    Instant createdAt
) {
}

