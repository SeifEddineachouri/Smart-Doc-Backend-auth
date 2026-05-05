package com.example.demo.model.dto;

public record RefreshTokenResponseDto(
    String accessToken,
    String tokenType,
    long expiresIn
) {
}


