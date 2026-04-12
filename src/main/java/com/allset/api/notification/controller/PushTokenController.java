package com.allset.api.notification.controller;

import com.allset.api.notification.dto.PushTokenResponse;
import com.allset.api.notification.dto.RegisterPushTokenRequest;
import com.allset.api.notification.service.PushTokenService;
import com.allset.api.shared.annotation.CurrentUser;
import com.allset.api.shared.exception.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Push Tokens", description = "Registro e remocao de tokens de push")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @Operation(summary = "Listar tokens do usuario autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = PushTokenResponse.class))))
    })
    @GetMapping
    public ResponseEntity<List<PushTokenResponse>> list(@CurrentUser UUID currentUserId) {
        return ResponseEntity.ok(pushTokenService.listForUser(currentUserId));
    }

    @Operation(summary = "Registrar ou atualizar token de push")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Token salvo com sucesso",
                    headers = @Header(name = "Location", description = "URI do token salvo",
                            schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PushTokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados invalidos",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<PushTokenResponse> register(
            @CurrentUser UUID currentUserId,
            @Valid @RequestBody RegisterPushTokenRequest request
    ) {
        PushTokenResponse response = pushTokenService.register(currentUserId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Remover token de push")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Token removido com sucesso", content = @Content),
            @ApiResponse(responseCode = "404", description = "Token nao encontrado",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID do token", required = true) @PathVariable UUID id,
            @CurrentUser UUID currentUserId
    ) {
        pushTokenService.delete(currentUserId, id);
        return ResponseEntity.noContent().build();
    }
}
