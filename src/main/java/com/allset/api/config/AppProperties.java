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
        Integer port,

        // --- Jobs ---

        @NotBlank(message = "USER_PURGE_CRON é obrigatório")
        @DefaultValue("0 0 2 * * *")
        String userPurgeCron,

        @NotBlank(message = "PUSH_TOKEN_PRUNE_CRON Ã© obrigatÃ³rio")
        @DefaultValue("0 0 3 * * *")
        String pushTokenPruneCron,

        // --- Auth: TTLs ---

        @DefaultValue("15")
        @Min(value = 1, message = "ACCESS_TOKEN_TTL_MINUTES deve ser maior que 0")
        Integer accessTokenTtlMinutes,

        @DefaultValue("7")
        @Min(value = 1, message = "REFRESH_TOKEN_TTL_DAYS deve ser maior que 0")
        Integer refreshTokenTtlDays,

        @DefaultValue("10")
        @Min(value = 1, message = "RESET_CODE_TTL_MINUTES deve ser maior que 0")
        Integer resetCodeTtlMinutes,

        // --- Email / Resend ---

        @NotBlank(message = "RESEND_API_KEY é obrigatório")
        String resendApiKey,

        @NotBlank(message = "EMAIL_FROM é obrigatório")
        String emailFrom,

        // --- Express ---

        @DefaultValue("10")
        @Min(value = 1, message = "EXPRESS_PRO_TIMEOUT_MINUTES deve ser maior que 0")
        Integer expressProTimeoutMinutes,

        @DefaultValue("30")
        @Min(value = 1, message = "EXPRESS_CLIENT_WINDOW_MINUTES deve ser maior que 0")
        Integer expressClientWindowMinutes,

        @DefaultValue("15")
        @Min(value = 1, message = "EXPRESS_SEARCH_RADIUS_KM deve ser maior que 0")
        Double expressSearchRadiusKm,

        @DefaultValue("10")
        @Min(value = 1, message = "EXPRESS_MAX_QUEUE_SIZE deve ser maior que 0")
        Integer expressMaxQueueSize,

        @DefaultValue("3")
        @Min(value = 1, message = "EXPRESS_MAX_SEARCH_ATTEMPTS deve ser maior que 0")
        Integer expressMaxSearchAttempts,

        @DefaultValue("50")
        @Min(value = 1, message = "EXPRESS_MAX_RADIUS_KM deve ser maior que 0")
        Double expressMaxRadiusKm,

        // --- CORS / Frontend ---

        /**
         * URL do frontend permitida para conexões WebSocket e CORS.
         * Em produção deve ser o domínio exato (ex: https://app.allset.com.br).
         * Em desenvolvimento aceita qualquer origem por padrão.
         */
        @DefaultValue("*")
        String frontendUrl,

        // --- Chat ---

        @DefaultValue("4000")
        @Min(value = 1, message = "CHAT_MESSAGE_MAX_LENGTH deve ser maior que 0")
        @Max(value = 10000, message = "CHAT_MESSAGE_MAX_LENGTH não pode exceder 10000")
        Integer chatMessageMaxLength,

        @DefaultValue("50")
        @Min(value = 1, message = "CHAT_MESSAGE_PAGE_SIZE deve ser maior que 0")
        @Max(value = 200, message = "CHAT_MESSAGE_PAGE_SIZE não pode exceder 200")
        Integer chatMessagePageSize

) {}
