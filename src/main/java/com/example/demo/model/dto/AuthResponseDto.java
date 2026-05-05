package com.example.demo.model.dto;

public record AuthResponseDto(
    String accessToken,
    String tokenType,
    long expiresIn,
    AuthUserProfileDto user
) {
}


