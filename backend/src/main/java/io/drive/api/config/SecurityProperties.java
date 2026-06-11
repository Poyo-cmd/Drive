package io.drive.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "drive.security")
public record SecurityProperties(
        String jwtSecret,
        long jwtExpirationMinutes
) {
}
