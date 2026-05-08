package com.allset.api.calendar.dto;

import com.allset.api.shared.validation.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateBlockedPeriodRequest(

        @Schema(description = "Dia da semana — aplicável a bloqueios recorrentes. 0=Dom, 1=Seg … 6=Sáb")
        @Min(value = 0, message = "Dia da semana deve estar entre 0 (Dom) e 6 (Sáb)")
        @Max(value = 6, message = "Dia da semana deve estar entre 0 (Dom) e 6 (Sáb)")
        Short weekday,

        @Schema(description = "Data específica — aplicável a bloqueios de data específica")
        LocalDate specificDate,

        @Schema(description = "Horário de início — null = dia inteiro bloqueado")
        LocalTime startsAt,

        @Schema(description = "Horário de fim — null = dia inteiro bloqueado")
        LocalTime endsAt,

        @Schema(description = "Início do bloqueio por pedido — aplicável a bloqueios do tipo order")
        Instant orderStartsAt,

        @Schema(description = "Fim do bloqueio por pedido — aplicável a bloqueios do tipo order")
        Instant orderEndsAt,

        @Schema(description = "Motivo do bloqueio")
        @NoHtml
        @Size(max = 500, message = "Motivo deve ter no maximo 500 caracteres")
        String reason
) {}
