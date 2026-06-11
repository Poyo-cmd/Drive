package io.drive.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del endpoint AWS. Apunta a Floci en local, pero al usar el SDK
 * estandar basta cambiar estas propiedades para apuntar a AWS real.
 */
@ConfigurationProperties(prefix = "drive.aws")
public record AwsProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        String metadataTable,
        String usersTable
) {
}
