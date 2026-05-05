package com.example.demo.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AskQuestionRequestDto(
    @NotBlank String question,
    @NotEmpty List<@Valid UploadedDocumentDto> documents
) {
}


