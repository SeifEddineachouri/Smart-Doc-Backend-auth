package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    int refreshShortDays,
    int refreshLongDays,
    String refreshCookieName,
    boolean refreshCookieSecure
) {
}

