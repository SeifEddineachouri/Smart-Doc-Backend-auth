package com.example.demo.service;

import com.example.demo.model.dto.AuthResponseDto;

public record AuthTokens(
    AuthResponseDto authResponse,
    String refreshToken,
    long refreshTokenMaxAgeSeconds
) {
}


