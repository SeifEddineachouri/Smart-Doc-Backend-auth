package com.example.demo.service;

import com.example.demo.model.dto.RefreshTokenResponseDto;

public record RefreshTokens(
    RefreshTokenResponseDto response,
    String refreshToken,
    long refreshTokenMaxAgeSeconds
) {
}


