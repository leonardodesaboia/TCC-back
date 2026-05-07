package com.allset.api.notification.controller;

import com.allset.api.notification.dto.MarkAllNotificationsReadResponse;
import com.allset.api.notification.dto.NotificationPreferenceResponse;
import com.allset.api.notification.dto.NotificationResponse;
import com.allset.api.notification.dto.UpdateNotificationPreferenceRequest;
import com.allset.api.notification.service.NotificationService;
import com.allset.api.shared.annotation.CurrentUser;
import com.allset.api.shared.exception.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Notificacoes", description = "Preferencias e historico de notificacoes do usuario autenticado")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Listar notificacoes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido", content = @Content)
    })
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> list(
            @CurrentUser UUID currentUserId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(notificationService.listForUser(currentUserId, pageable));
    }

    @Operation(summary = "Marcar notificacao como lida")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificacao marcada como lida",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Notificacao nao encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @Parameter(description = "ID da notificacao", required = true) @PathVariable UUID id,
            @CurrentUser UUID currentUserId
    ) {
        return ResponseEntity.ok(notificationService.markAsRead(currentUserId, id));
    }

    @Operation(summary = "Marcar todas as notificacoes como lidas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificacoes marcadas como lidas",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MarkAllNotificationsReadResponse.class)))
    })
    @PatchMapping("/read-all")
    public ResponseEntity<MarkAllNotificationsReadResponse> markAllAsRead(
            @CurrentUser UUID currentUserId
    ) {
        int markedCount = notificationService.markAllAsRead(currentUserId);
        return ResponseEntity.ok(new MarkAllNotificationsReadResponse(markedCount));
    }

    @Operation(summary = "Consultar preferencia global de notificacao")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferencia retornada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = NotificationPreferenceResponse.class)))
    })
    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> getPreference(
            @CurrentUser UUID currentUserId
    ) {
        return ResponseEntity.ok(notificationService.getPreference(currentUserId));
    }

    @Operation(summary = "Atualizar preferencia global de notificacao")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferencia atualizada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = NotificationPreferenceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados invalidos",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> updatePreference(
            @CurrentUser UUID currentUserId,
            @Valid @RequestBody UpdateNotificationPreferenceRequest request
    ) {
        return ResponseEntity.ok(notificationService.updatePreference(currentUserId, request));
    }
}
