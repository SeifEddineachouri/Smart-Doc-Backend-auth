package com.example.demo.service;

import com.example.demo.model.dto.AuthResponseDto;
import com.example.demo.model.dto.AuthUserProfileDto;
import com.example.demo.model.dto.RefreshTokenResponseDto;
import com.example.demo.model.dto.SignInRequestDto;
import com.example.demo.model.dto.SignUpRequestDto;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.config.AuthProperties;
import com.example.demo.model.enums.LanguageCode;
import com.example.demo.model.entity.RefreshTokenEntity;
import com.example.demo.model.entity.RoleEntity;
import com.example.demo.model.enums.RoleName;
import com.example.demo.model.entity.UserEntity;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.JwtTokenUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final AuthProperties authProperties;

    public AuthService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtTokenUtil jwtTokenUtil,
        AuthProperties authProperties
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.authProperties = authProperties;
    }

    @Transactional
    public AuthTokens signup(SignUpRequestDto request) {
        String normalizedEmail = normalizeEmail(request.workEmail());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account already exists with this email");
        }

        RoleEntity defaultRole = roleRepository.findByName(RoleName.ROLE_USER)
            .orElseThrow(() -> new IllegalStateException("ROLE_USER is missing in database"));

        UserEntity user = new UserEntity();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAcceptedTerms(request.acceptedTerms());
        user.setLanguage(LanguageCode.en);
        user.getRoles().add(defaultRole);

        UserEntity saved = userRepository.save(user);
        return issueTokens(saved, false);
    }

    @Transactional
    public AuthTokens signin(SignInRequestDto request) {
        String normalizedEmail = normalizeEmail(request.email());
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));
        } catch (BadCredentialsException ex) {
            throw new UnauthorizedException("Invalid credentials");
        }

        UserEntity user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        return issueTokens(user, request.rememberMe());
    }

    @Transactional
    public RefreshTokens refresh(String refreshTokenRaw) {
        if (refreshTokenRaw == null || refreshTokenRaw.isBlank()) {
            throw new UnauthorizedException("Refresh token is required");
        }

        String tokenHash = hashToken(refreshTokenRaw);
        RefreshTokenEntity stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new UnauthorizedException("Refresh token has expired");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        AuthTokens tokens = issueTokens(stored.getUser(), stored.isRememberMe());
        RefreshTokenResponseDto response = new RefreshTokenResponseDto(
            tokens.authResponse().accessToken(),
            tokens.authResponse().tokenType(),
            tokens.authResponse().expiresIn()
        );

        return new RefreshTokens(response, tokens.refreshToken(), tokens.refreshTokenMaxAgeSeconds());
    }

    @Transactional
    public void signout(String refreshTokenRaw) {
        if (refreshTokenRaw == null || refreshTokenRaw.isBlank()) {
            return;
        }
        String tokenHash = hashToken(refreshTokenRaw);
        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private AuthTokens issueTokens(UserEntity user, boolean rememberMe) {
        String accessToken = jwtTokenUtil.generateAccessToken(user);
        String refreshTokenRaw = UUID.randomUUID() + "." + UUID.randomUUID();

        Instant expiresAt = Instant.now().plus(refreshDays(rememberMe), ChronoUnit.DAYS);

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
        refreshTokenEntity.setUser(user);
        refreshTokenEntity.setTokenHash(hashToken(refreshTokenRaw));
        refreshTokenEntity.setExpiresAt(expiresAt);
        refreshTokenEntity.setRememberMe(rememberMe);
        refreshTokenEntity.setRevoked(false);
        refreshTokenRepository.save(refreshTokenEntity);

        AuthUserProfileDto userProfile = new AuthUserProfileDto(
            user.getId().toString(),
            user.getFullName(),
            user.getEmail(),
            user.getLanguage().name()
        );

        AuthResponseDto response = new AuthResponseDto(accessToken, "Bearer", jwtTokenUtil.getAccessTokenSeconds(), userProfile);
        long maxAge = refreshDays(rememberMe) * 24L * 60L * 60L;
        return new AuthTokens(response, refreshTokenRaw, maxAge);
    }

    private int refreshDays(boolean rememberMe) {
        return rememberMe ? authProperties.refreshLongDays() : authProperties.refreshShortDays();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Missing SHA-256 algorithm", ex);
        }
    }
}


