package com.example.demo.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateLanguageRequestDto(
    @NotBlank @Pattern(regexp = "en|fr") String language
) {
}


