package com.example.demo.controller;

import com.example.demo.model.dto.AuthResponseDto;
import com.example.demo.model.dto.RefreshTokenRequestDto;
import com.example.demo.model.dto.RefreshTokenResponseDto;
import com.example.demo.model.dto.SignInRequestDto;
import com.example.demo.model.dto.SignUpRequestDto;
import com.example.demo.service.AuthService;
import com.example.demo.service.AuthTokens;
import com.example.demo.service.RefreshTokens;
import com.example.demo.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthProperties authProperties;

    public AuthController(AuthService authService, AuthProperties authProperties) {
        this.authService = authService;
        this.authProperties = authProperties;
    }

    @PostMapping({"/signup", "/register"})
    public ResponseEntity<AuthResponseDto> signup(@Valid @RequestBody SignUpRequestDto request) {
        AuthTokens tokens = authService.signup(request);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(tokens.refreshToken(), tokens.refreshTokenMaxAgeSeconds()).toString())
            .body(tokens.authResponse());
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponseDto> signin(@Valid @RequestBody SignInRequestDto request) {
        AuthTokens tokens = authService.signin(request);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(tokens.refreshToken(), tokens.refreshTokenMaxAgeSeconds()).toString())
            .body(tokens.authResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponseDto> refresh(
        @RequestBody(required = false) RefreshTokenRequestDto request,
        HttpServletRequest httpRequest
    ) {
        String refreshTokenFromCookie = extractRefreshTokenFromCookies(httpRequest);
        String refreshToken = refreshTokenFromCookie;
        if ((refreshToken == null || refreshToken.isBlank()) && request != null) {
            refreshToken = request.refreshToken();
        }
        RefreshTokens tokens = authService.refresh(refreshToken);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(tokens.refreshToken(), tokens.refreshTokenMaxAgeSeconds()).toString())
            .body(tokens.response());
    }

    @PostMapping("/signout")
    public ResponseEntity<Void> signout(HttpServletRequest request) {
        String refreshToken = extractRefreshTokenFromCookies(request);
        authService.signout(refreshToken);
        ResponseCookie clearCookie = buildRefreshCookie("", 0);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie.toString()).build();
    }

    private ResponseCookie buildRefreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(authProperties.refreshCookieName(), value)
            .httpOnly(true)
            .secure(authProperties.refreshCookieSecure())
            .path("/")
            .sameSite("Lax")
            .maxAge(maxAgeSeconds)
            .build();
    }

    private String extractRefreshTokenFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (authProperties.refreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}




