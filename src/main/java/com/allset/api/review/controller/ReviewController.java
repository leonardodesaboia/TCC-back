package com.allset.api.review.controller;

import com.allset.api.review.dto.CreateReviewRequest;
import com.allset.api.review.dto.ReviewResponse;
import com.allset.api.review.service.ReviewService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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

@Tag(name = "Avaliacoes", description = "Avaliacoes de pedidos, profissionais e clientes")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Enviar avaliacao de um pedido concluido")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Avaliacao enviada com sucesso",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ReviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "Regra de negocio violada",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Pedido nao encontrado",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Avaliacao ja enviada para este pedido",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/orders/{orderId}/reviews")
    @PreAuthorize("hasAnyAuthority('client', 'professional')")
    public ResponseEntity<ReviewResponse> create(
            @Parameter(description = "ID do pedido", required = true) @PathVariable UUID orderId,
            @CurrentUser UUID currentUserId,
            Authentication authentication,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority())
                .orElse("client");

        ReviewResponse response = reviewService.create(orderId, currentUserId, role, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Listar avaliacoes de um pedido")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avaliacoes retornadas com sucesso",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ReviewResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pedido nao encontrado",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/orders/{orderId}/reviews")
    public ResponseEntity<List<ReviewResponse>> listOrderReviews(
            @Parameter(description = "ID do pedido", required = true) @PathVariable UUID orderId,
            @CurrentUser UUID currentUserId,
            Authentication authentication
    ) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority())
                .orElse("client");

        return ResponseEntity.ok(reviewService.listOrderReviews(orderId, currentUserId, role));
    }

    @Operation(summary = "Listar avaliacoes publicadas de um profissional")
    @ApiResponse(responseCode = "200", description = "Avaliacoes retornadas com sucesso")
    @GetMapping("/professionals/{professionalId}/reviews")
    public ResponseEntity<Page<ReviewResponse>> listProfessionalReviews(
            @Parameter(description = "ID do profissional", required = true) @PathVariable UUID professionalId,
            @ParameterObject @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(reviewService.listProfessionalReviews(professionalId, pageable));
    }

    @Operation(summary = "Listar avaliacoes publicadas de um servico especifico do profissional")
    @ApiResponse(responseCode = "200", description = "Avaliacoes retornadas com sucesso")
    @GetMapping("/professionals/{professionalId}/services/{serviceId}/reviews")
    public ResponseEntity<Page<ReviewResponse>> listServiceReviews(
            @Parameter(description = "ID do profissional", required = true) @PathVariable UUID professionalId,
            @Parameter(description = "ID do servico", required = true) @PathVariable UUID serviceId,
            @ParameterObject @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(reviewService.listServiceReviews(professionalId, serviceId, pageable));
    }
}
