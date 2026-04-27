package com.allset.api.chat.controller;

import com.allset.api.chat.dto.*;
import com.allset.api.chat.service.ConversationService;
import com.allset.api.chat.service.MessageService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Chat", description = "Conversas e mensagens entre cliente e profissional")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    // ─────────────────────────────────────────
    // Conversas
    // ─────────────────────────────────────────

    @Operation(
            summary = "Listar conversas do usuário autenticado",
            description = "Retorna página de conversas onde o usuário autenticado é participante, "
                        + "ordenadas pela última mensagem (decrescente)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de conversas",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ConversationSummaryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content)
    })
    @GetMapping
    public ResponseEntity<Page<ConversationSummaryResponse>> list(
            @CurrentUser UUID currentUserId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(conversationService.listForUser(currentUserId, pageable));
    }

    @Operation(
            summary = "Buscar conversa por ID",
            description = "Retorna detalhes da conversa. Retorna 404 se não existir ou se o "
                        + "usuário autenticado não for participante."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados da conversa",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ConversationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getById(
            @Parameter(description = "ID da conversa", required = true) @PathVariable UUID id,
            @CurrentUser UUID currentUserId
    ) {
        return ResponseEntity.ok(conversationService.getById(id, currentUserId));
    }

    // ─────────────────────────────────────────
    // Mensagens
    // ─────────────────────────────────────────

    @Operation(
            summary = "Listar mensagens da conversa",
            description = "Retorna histórico paginado de mensagens. Retorna 404 se o usuário "
                        + "autenticado não for participante."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Histórico de mensagens",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}/messages")
    public ResponseEntity<Page<MessageResponse>> listMessages(
            @Parameter(description = "ID da conversa", required = true) @PathVariable UUID id,
            @CurrentUser UUID currentUserId,
            @ParameterObject @PageableDefault(size = 50, sort = "sentAt",
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(messageService.list(id, currentUserId, pageable));
    }

    @Operation(
            summary = "Enviar mensagem de texto",
            description = "Persiste a mensagem e publica em tempo real no tópico STOMP "
                        + "/topic/conversations/{id}. Retorna 404 se o usuário autenticado "
                        + "não for participante."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mensagem enviada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dados inválidos",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @Parameter(description = "ID da conversa", required = true) @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request,
            @CurrentUser UUID currentUserId
    ) {
        MessageResponse response = messageService.sendText(id, currentUserId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/conversations/{convId}/messages/{msgId}")
                .buildAndExpand(id, response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(
            summary = "Enviar mensagem com imagem",
            description = "Faz upload da imagem (JPEG/PNG) e persiste a mensagem como tipo `image`. "
                        + "Retorna 404 se o usuário autenticado não for participante."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mensagem com imagem enviada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Tipo de arquivo inválido",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "413", description = "Arquivo excede o tamanho máximo permitido",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(value = "/{id}/messages/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> sendImageMessage(
            @Parameter(description = "ID da conversa", required = true) @PathVariable UUID id,
            @Parameter(description = "Arquivo de imagem (JPEG/PNG)", required = true)
            @RequestPart("file") MultipartFile file,
            @CurrentUser UUID currentUserId
    ) {
        MessageResponse response = messageService.sendImageMessage(id, currentUserId, file);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/conversations/{convId}/messages/{msgId}")
                .buildAndExpand(id, response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    // ─────────────────────────────────────────
    // Recibos de leitura
    // ─────────────────────────────────────────

    @Operation(
            summary = "Marcar mensagens como lidas",
            description = "Marca como lidas todas as mensagens enviadas pelo outro participante "
                        + "que ainda não foram lidas. Publica ReadReceiptEvent no tópico STOMP."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recibo de leitura",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ReadReceiptEvent.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{id}/read")
    public ResponseEntity<ReadReceiptEvent> markAsRead(
            @Parameter(description = "ID da conversa", required = true) @PathVariable UUID id,
            @CurrentUser UUID currentUserId
    ) {
        ReadReceiptEvent event = messageService.markAsRead(id, currentUserId);
        return ResponseEntity.ok(event);
    }
}
