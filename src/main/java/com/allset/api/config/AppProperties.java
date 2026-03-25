package com.allset.api.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties
public record AppProperties(

        // --- Segurança ---

        @NotBlank(message = "JWT_SECRET é obrigatório")
        String jwtSecret,

        @NotBlank(message = "CPF_ENCRYPTION_KEY é obrigatório")
        @Pattern(
                regexp = "[0-9a-fA-F]{64}",
                message = "CPF_ENCRYPTION_KEY deve conter exatamente 64 caracteres hexadecimais (32 bytes)"
        )
        String cpfEncryptionKey,

        // --- Banco de dados ---

        @NotBlank(message = "DATABASE_URL é obrigatório")
        String databaseUrl,

        @NotBlank(message = "DB_USER é obrigatório")
        String dbUser,

        @NotBlank(message = "DB_PASS é obrigatório")
        String dbPass,

        // --- Redis ---

        @DefaultValue("localhost")
        String redisHost,

        @DefaultValue("6379")
        @Min(value = 1, message = "REDIS_PORT deve ser maior que 0")
        @Max(value = 65535, message = "REDIS_PORT deve ser menor que 65535")
        Integer redisPort,

        // --- Servidor ---

        @DefaultValue("8080")
        @Min(value = 1, message = "PORT deve ser maior que 0")
        @Max(value = 65535, message = "PORT deve ser menor que 65535")
        Integer port

) {}
