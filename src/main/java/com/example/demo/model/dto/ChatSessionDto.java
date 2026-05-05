package com.example.demo.model.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionDto(
    UUID id,
    String name,
    Instant createdAt,
    Instant updatedAt,
    String lastQuestion
) {
}
