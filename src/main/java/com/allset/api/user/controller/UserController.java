package com.allset.api.user.controller;

import com.allset.api.shared.exception.ApiError;
import com.allset.api.user.dto.BanUserRequest;
import com.allset.api.user.dto.CreateUserRequest;
import com.allset.api.user.dto.UpdateUserRequest;
import com.allset.api.user.dto.UserResponse;
import com.allset.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
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
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Tag(name = "Usuários", description = "Gerenciamento de usuários da plataforma")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
        summary = "Criar usuário",
        description = "Cria um novo usuário na plataforma."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Usuário criado com sucesso",
            headers = @Header(name = "Location", description = "URI do novo recurso", schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado — requer role admin", content = @Content),
        @ApiResponse(responseCode = "409", description = "Email ou CPF já cadastrado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.create(request);
        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(
        summary = "Listar usuários",
        description = "Retorna usuários de forma paginada. Por padrão retorna apenas ativos. "
                    + "Use `?banned=true` para listar banidos ou `?deleted=true` para soft-deletados."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado — requer role admin", content = @Content)
    })
    @GetMapping
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<Page<UserResponse>> findAll(
        @Parameter(description = "Filtrar usuários banidos") @RequestParam(defaultValue = "false") boolean banned,
        @Parameter(description = "Filtrar usuários removidos (soft delete)") @RequestParam(defaultValue = "false") boolean deleted,
        @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(userService.findAll(banned, deleted, pageable));
    }

    @Operation(
        summary = "Buscar usuário por ID",
        description = "Retorna os dados de um usuário pelo seu ID. "
                    + "Permitido ao próprio usuário (identificado pelo `sub` do JWT) ou a administradores."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuário encontrado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
        @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('admin') or #id.toString() == authentication.name")
    public ResponseEntity<UserResponse> findById(
        @Parameter(description = "ID do usuário", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @Operation(
        summary = "Atualizar usuário",
        description = "Atualiza os dados de um usuário (nome, email, telefone, avatarUrl). "
                    + "Apenas os campos informados são alterados. "
                    + "Permitido ao próprio usuário ou a administradores."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuário atualizado com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
        @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Email já utilizado por outro usuário",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('admin') or #id.toString() == authentication.name")
    public ResponseEntity<UserResponse> update(
        @Parameter(description = "ID do usuário", required = true) @PathVariable UUID id,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @Operation(
        summary = "Deletar conta (soft delete)",
        description = "Inicia o processo de exclusão da conta. "
                    + "A conta permanece salva por **30 dias** — durante esse período o usuário pode reativá-la. "
                    + "Após 30 dias a conta é permanentemente excluída. "
                    + "Permitido ao próprio usuário ou a administradores."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Solicitação de exclusão registrada — retorna dados com `scheduledDeletionAt`",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
        @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('admin') or #id.toString() == authentication.name")
    public ResponseEntity<UserResponse> softDelete(
        @Parameter(description = "ID do usuário", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(userService.softDelete(id));
    }

    @Operation(
        summary = "Reativar conta",
        description = "Cancela a exclusão pendente e restaura a conta ao estado ativo. "
                    + "Disponível enquanto a conta estiver dentro do período de graça de 30 dias. "
                    + "Permitido ao próprio usuário ou a administradores."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conta reativada com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado", content = @Content),
        @ApiResponse(responseCode = "404", description = "Conta não encontrada ou já permanentemente excluída",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('admin') or #id.toString() == authentication.name")
    public ResponseEntity<UserResponse> reactivate(
        @Parameter(description = "ID do usuário", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(userService.reactivate(id));
    }

    @Operation(
        summary = "Banir usuário",
        description = "Desativa o usuário e registra o motivo do banimento. Exclusivo para administradores."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuário banido com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Motivo não informado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado — requer role admin", content = @Content),
        @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{id}/ban")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<UserResponse> ban(
        @Parameter(description = "ID do usuário", required = true) @PathVariable UUID id,
        @Valid @RequestBody BanUserRequest request
    ) {
        return ResponseEntity.ok(userService.ban(id, request));
    }

    @Operation(
        summary = "Reativar usuário",
        description = "Reativa um usuário previamente banido, limpando o motivo do banimento. "
                    + "Exclusivo para administradores."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuário reativado com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado — requer role admin", content = @Content),
        @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/{id}/activate")
    public ResponseEntity<UserResponse> activate(
        @Parameter(description = "ID do usuário", required = true) @PathVariable UUID id
    ) {
        return ResponseEntity.ok(userService.activate(id));
    }
}
