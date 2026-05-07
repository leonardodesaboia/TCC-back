package com.allset.api.integration.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Referência a um arquivo armazenado no storage")
public record StorageRefResponse(

        @Schema(description = "Chave do objeto no bucket", example = "professionals/abc/cnh/xyz.jpg")
        String key,

        @Schema(description = "URL para download (pré-assinada com TTL curto em buckets privados)",
                example = "http://localhost:9000/allset-documents/...")
        String downloadUrl,

        @Schema(description = "Instante de expiração da URL — null em buckets públicos",
                example = "2026-04-25T15:00:00Z")
        Instant urlExpiresAt
) {}
