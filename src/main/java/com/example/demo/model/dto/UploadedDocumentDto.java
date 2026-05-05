package com.example.demo.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadedDocumentDto(
    @NotNull Long id,
    @NotBlank String name,
    @NotBlank String size,
    @NotBlank String mimeType,
    java.util.UUID sessionId
) {
}



