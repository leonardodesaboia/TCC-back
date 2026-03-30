package com.allset.api.calendar.dto;

import com.allset.api.calendar.domain.BlockType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BlockedPeriodResponse(

        @Schema(description = "ID do bloqueio") UUID id,
        @Schema(description = "ID do profissional") UUID professionalId,
        @Schema(description = "Tipo do bloqueio") BlockType blockType,
        @Schema(description = "Dia da semana (0=Dom … 6=Sáb)") Short weekday,
        @Schema(description = "Data específica") LocalDate specificDate,
        @Schema(description = "Horário de início") LocalTime startsAt,
        @Schema(description = "Horário de fim") LocalTime endsAt,
        @Schema(description = "ID do pedido vinculado") UUID orderId,
        @Schema(description = "Início do bloqueio por pedido") Instant orderStartsAt,
        @Schema(description = "Fim do bloqueio por pedido") Instant orderEndsAt,
        @Schema(description = "Motivo do bloqueio") String reason,
        @Schema(description = "Data de criação") Instant createdAt
) {}
