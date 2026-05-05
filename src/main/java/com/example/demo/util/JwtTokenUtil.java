package com.example.demo.util;

import com.example.demo.model.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenUtil {

    private final SecretKey signingKey;
    private final long accessTokenSeconds;

    public JwtTokenUtil(
        @Value("${app.jwt.secret}") String jwtSecret,
        @Value("${app.jwt.access-token-seconds:3600}") long accessTokenSeconds
    ) {
        this.signingKey = Keys.hmacShaKeyFor(decodeSecret(jwtSecret));
        this.accessTokenSeconds = accessTokenSeconds;
    }

    public long getAccessTokenSeconds() {
        return accessTokenSeconds;
    }

    public String generateAccessToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenSeconds);

        return Jwts.builder()
            .subject(user.getEmail())
            .claims(Map.of("uid", user.getId().toString(), "lang", user.getLanguage().name()))
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, String expectedUsername) {
        String username = extractUsername(token);
        return username.equals(expectedUsername) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        return expiration.before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return resolver.apply(claims);
    }

    private byte[] decodeSecret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ignored) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}





