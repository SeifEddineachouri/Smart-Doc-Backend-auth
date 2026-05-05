package com.example.demo.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequestDto(
    @NotBlank @Size(min = 2, max = 255) String fullName,
    @NotBlank @Email String workEmail,
    @NotBlank @Size(min = 8, max = 255) String password,
    @AssertTrue boolean acceptedTerms
) {
}


