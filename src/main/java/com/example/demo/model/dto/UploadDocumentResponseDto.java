package com.example.demo.model.dto;

public record UploadDocumentResponseDto(
    Long id,
    String name,
    String size,
    String mimeType,
    java.util.UUID sessionId
) {
}


