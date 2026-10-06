package com.allset.api.order.controller;

import com.allset.api.order.domain.OrderMode;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.dto.OrderResponse;
import com.allset.api.order.dto.ProposeNewPriceRequest;
import com.allset.api.order.dto.ReportScopeMismatchRequest;
import com.allset.api.order.dto.RespondNewPriceRequest;
import com.allset.api.order.exception.OrderNotFoundException;
import com.allset.api.order.exception.OrderStatusTransitionException;
import com.allset.api.order.service.OrderService;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.shared.annotation.CurrentUser;
import com.allset.api.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de contrato dos 3 endpoints novos desta change (report-scope-mismatch,
 * express/propose-new-price, express/respond-new-price), rodando MockMvc em modo standalone
 * — sem subir o ApplicationContext.
 *
 * Motivo de não usar @SpringBootTest/@WebMvcTest: a suíte de integração do projeto já está
 * travada por dois bugs pré-existentes não relacionados a este módulo, documentados em
 * tasks.md #7.1 (GEOCODING_USER_AGENT ausente trava o carregamento do contexto em todo teste
 * @SpringBootTest; birth_date NOT NULL quebra createUser nos testes de integração). Rodar em
 * modo standalone mantém estes testes independentes desses bugs, validando o que realmente
 * importa neste nível: roteamento para o service certo, Bean Validation real dos DTOs (não
 * mockada) e o mapeamento de exceções de negócio para status HTTP via GlobalExceptionHandler.
 *
 * Fora do escopo destes testes: autorização (@PreAuthorize) — método security não é ativado
 * em modo standalone; cobrir isso exige um teste de integração completo (quando os bugs acima
 * forem corrigidos), não é algo que MockMvc standalone consiga validar honestamente.
 */
class OrderControllerTest {

    private final OrderService orderService = mock(OrderService.class);
    private final ProfessionalRepository professionalRepository = mock(ProfessionalRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        OrderController controller = new OrderController(orderService, professionalRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(fixedCurrentUser(currentUserId))
                .build();
    }

    private HandlerMethodArgumentResolver fixedCurrentUser(UUID userId) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(CurrentUser.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return userId;
            }
        };
    }

    // ─────────────────────────────────────────
    // report-scope-mismatch
    // ─────────────────────────────────────────

    @Test
    void reportScopeMismatchShouldDelegateToServiceAndReturnItsResponse() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.reportScopeMismatch(eq(orderId), eq(currentUserId), any()))
                .thenReturn(orderResponse(orderId, OrderStatus.cancelled, true));

        mockMvc.perform(post("/api/v1/orders/{id}/report-scope-mismatch", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReportScopeMismatchRequest("Era troca de disjuntor, não de tomada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.scopeMismatch").value(true));
    }

    @Test
    void reportScopeMismatchShouldRejectBlankDescriptionWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/report-scope-mismatch", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportScopeMismatchRequest(""))))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).reportScopeMismatch(any(), any(), any());
    }

    @Test
    void reportScopeMismatchShouldRejectDescriptionContainingHtmlWithoutCallingService() throws Exception {
        // Cenário explícito da spec: "com descrição ... contendo HTML/script" deve ser 400.
        mockMvc.perform(post("/api/v1/orders/{id}/report-scope-mismatch", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReportScopeMismatchRequest("<script>alert(1)</script>"))))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).reportScopeMismatch(any(), any(), any());
    }

    @Test
    void reportScopeMismatchShouldMapStatusTransitionErrorTo400() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.reportScopeMismatch(eq(orderId), eq(currentUserId), any()))
                .thenThrow(new OrderStatusTransitionException(OrderStatus.pending, "sinalização de escopo divergente"));

        mockMvc.perform(post("/api/v1/orders/{id}/report-scope-mismatch", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportScopeMismatchRequest("Escopo diferente"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportScopeMismatchShouldMapOwnershipFailureTo404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.reportScopeMismatch(eq(orderId), eq(currentUserId), any()))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(post("/api/v1/orders/{id}/report-scope-mismatch", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReportScopeMismatchRequest("Escopo diferente"))))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────
    // express/propose-new-price
    // ─────────────────────────────────────────

    @Test
    void proposeNewPriceShouldDelegateToServiceAndReturnItsResponse() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.proposeNewPrice(eq(orderId), eq(currentUserId), any()))
                .thenReturn(orderResponse(orderId, OrderStatus.accepted, false));

        mockMvc.perform(post("/api/v1/orders/{id}/express/propose-new-price", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProposeNewPriceRequest(new BigDecimal("150.00"), "Era troca de disjuntor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void proposeNewPriceShouldRejectNonPositiveAmountWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/express/propose-new-price", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProposeNewPriceRequest(new BigDecimal("0.00"), "Motivo qualquer"))))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).proposeNewPrice(any(), any(), any());
    }

    @Test
    void proposeNewPriceShouldRejectAmountWithMoreThanTwoDecimalDigitsWithoutCallingService() throws Exception {
        // Regressão do achado de code review (design.md): sem @Digits, um valor com mais
        // casas decimais do que a coluna NUMERIC(10,2) comporta estourava 500 no flush do
        // Hibernate em vez de 400 de validação. Este teste garante que o 400 acontece antes
        // de qualquer chamada ao service — não só que o DTO tem a anotação certa.
        mockMvc.perform(post("/api/v1/orders/{id}/express/propose-new-price", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProposeNewPriceRequest(new BigDecimal("150.999"), "Motivo qualquer"))))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).proposeNewPrice(any(), any(), any());
    }

    @Test
    void proposeNewPriceShouldRejectBlankReasonWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/express/propose-new-price", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProposeNewPriceRequest(new BigDecimal("150.00"), ""))))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).proposeNewPrice(any(), any(), any());
    }

    @Test
    void proposeNewPriceShouldMapAlreadyPendingProposalErrorTo400() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.proposeNewPrice(eq(orderId), eq(currentUserId), any()))
                .thenThrow(new OrderStatusTransitionException(
                        OrderStatus.accepted, "proposta de novo preço com proposta já pendente"));

        mockMvc.perform(post("/api/v1/orders/{id}/express/propose-new-price", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ProposeNewPriceRequest(new BigDecimal("150.00"), "Outra divergência"))))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────
    // express/respond-new-price
    // ─────────────────────────────────────────

    @Test
    void respondNewPriceShouldDelegateAcceptanceAndReturnRecalculatedOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.respondNewPrice(eq(orderId), eq(currentUserId), any()))
                .thenReturn(orderResponse(orderId, OrderStatus.accepted, false));

        mockMvc.perform(post("/api/v1/orders/{id}/express/respond-new-price", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RespondNewPriceRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void respondNewPriceShouldDelegateRejectionAndReturnCancelledOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.respondNewPrice(eq(orderId), eq(currentUserId), any()))
                .thenReturn(orderResponse(orderId, OrderStatus.cancelled, true));

        mockMvc.perform(post("/api/v1/orders/{id}/express/respond-new-price", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RespondNewPriceRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.scopeMismatch").value(true));
    }

    @Test
    void respondNewPriceShouldMapNoPendingProposalErrorTo400() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.respondNewPrice(eq(orderId), eq(currentUserId), any()))
                .thenThrow(new OrderStatusTransitionException(
                        OrderStatus.accepted, "resposta a proposta de novo preço sem proposta pendente"));

        mockMvc.perform(post("/api/v1/orders/{id}/express/respond-new-price", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RespondNewPriceRequest(true))))
                .andExpect(status().isBadRequest());
    }

    private OrderResponse orderResponse(UUID orderId, OrderStatus status, boolean scopeMismatch) {
        return new OrderResponse(
                orderId, UUID.randomUUID(), UUID.randomUUID(), null, null, UUID.randomUUID(),
                null, null, null,
                OrderMode.express, status, "Trocar tomada", UUID.randomUUID(), null,
                null, null, null,
                null, null, null, null,
                null, null, null, null, null, null,
                scopeMismatch, null, null, null,
                null, null, null, null,
                0, null, null,
                List.of(), null, null
        );
    }
}
