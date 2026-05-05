package com.example.demo.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignInRequestDto(
    @NotBlank @Email String email,
    @NotBlank String password,
    boolean rememberMe
) {
}


