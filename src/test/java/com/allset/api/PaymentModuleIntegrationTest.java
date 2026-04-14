package com.allset.api;

import com.allset.api.integration.asaas.dto.AsaasWebhookEvent;
import com.allset.api.payment.domain.*;
import com.allset.api.payment.dto.CreatePaymentRequest;
import com.allset.api.payment.repository.PaymentRepository;
import com.allset.api.payment.repository.PaymentStatusHistoryRepository;
import com.allset.api.payment.repository.PaymentTransactionRepository;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt-secret=test-secret-test-secret-test-secret-1234",
        "cpf-encryption-key=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "database-url=jdbc:postgresql://placeholder/test",
        "db-user=test",
        "db-pass=test",
        "redis-host=localhost",
        "redis-port=6379",
        "port=8080",
        "user-purge-cron=0 0 2 * * *",
        "push-token-prune-cron=0 0 3 * * *",
        "resend-api-key=test-key",
        "email-from=test@example.com",
        "asaas-api-key=test-asaas-key",
        "asaas-base-url=http://localhost:9999",
        "asaas-webhook-token=test-webhook-token"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PaymentModuleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentTransactionRepository transactionRepository;
    @Autowired private PaymentStatusHistoryRepository historyRepository;

    private final AtomicInteger sequence = new AtomicInteger(1);

    @BeforeEach
    void cleanDatabase() {
        historyRepository.deleteAll();
        transactionRepository.deleteAll();
        paymentRepository.deleteAll();
        userRepository.deleteAll();
        sequence.set(1);
    }

    // ─────────────────────────────────────────
    // GET /api/v1/payments — admin list
    // ─────────────────────────────────────────

    @Test
    void shouldListPaymentsAsAdmin() throws Exception {
        User admin = createUser(UserRole.admin);
        User client = createUser(UserRole.client);

        // Cria um pagamento diretamente no banco para teste de listagem
        Payment payment = Payment.builder()
                .orderId(UUID.randomUUID())
                .payerUserId(client.getId())
                .receiverProfessionalId(UUID.randomUUID())
                .status(PaymentStatus.pending)
                .method(PaymentMethod.pix)
                .grossAmount(new BigDecimal("200.00"))
                .platformFee(new BigDecimal("40.00"))
                .netAmount(new BigDecimal("160.00"))
                .build();
        paymentRepository.save(payment);

        mockMvc.perform(get("/api/v1/payments")
                        .with(jwtFor(admin.getId(), "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].grossAmount").value(200.00))
                .andExpect(jsonPath("$.content[0].status").value("pending"));
    }

    @Test
    void shouldDenyListPaymentsForNonAdmin() throws Exception {
        User client = createUser(UserRole.client);

        mockMvc.perform(get("/api/v1/payments")
                        .with(jwtFor(client.getId(), "client")))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────
    // GET /api/v1/payments/{id}
    // ─────────────────────────────────────────

    @Test
    void shouldGetPaymentById() throws Exception {
        User client = createUser(UserRole.client);

        Payment payment = Payment.builder()
                .orderId(UUID.randomUUID())
                .payerUserId(client.getId())
                .receiverProfessionalId(UUID.randomUUID())
                .status(PaymentStatus.confirmed)
                .method(PaymentMethod.credit_card)
                .grossAmount(new BigDecimal("500.00"))
                .platformFee(new BigDecimal("100.00"))
                .netAmount(new BigDecimal("400.00"))
                .paidAt(Instant.now())
                .build();
        payment = paymentRepository.save(payment);

        mockMvc.perform(get("/api/v1/payments/{id}", payment.getId())
                        .with(jwtFor(client.getId(), "client")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.method").value("credit_card"))
                .andExpect(jsonPath("$.grossAmount").value(500.00))
                .andExpect(jsonPath("$.platformFee").value(100.00))
                .andExpect(jsonPath("$.netAmount").value(400.00))
                .andExpect(jsonPath("$.transactions").isArray());
    }

    @Test
    void shouldReturn404ForNonExistentPayment() throws Exception {
        User client = createUser(UserRole.client);
        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/payments/{id}", fakeId)
                        .with(jwtFor(client.getId(), "client")))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────
    // GET /api/v1/orders/{orderId}/payment
    // ─────────────────────────────────────────

    @Test
    void shouldGetPaymentByOrderId() throws Exception {
        User client = createUser(UserRole.client);
        UUID orderId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .orderId(orderId)
                .payerUserId(client.getId())
                .receiverProfessionalId(UUID.randomUUID())
                .status(PaymentStatus.pending)
                .method(PaymentMethod.boleto)
                .grossAmount(new BigDecimal("300.00"))
                .platformFee(new BigDecimal("60.00"))
                .netAmount(new BigDecimal("240.00"))
                .invoiceUrl("https://boleto.url/123")
                .build();
        paymentRepository.save(payment);

        mockMvc.perform(get("/api/v1/orders/{orderId}/payment", orderId)
                        .with(jwtFor(client.getId(), "client")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.method").value("boleto"))
                .andExpect(jsonPath("$.invoiceUrl").value("https://boleto.url/123"));
    }

    // ─────────────────────────────────────────
    // POST /api/v1/payments/webhook/asaas
    // ─────────────────────────────────────────

    @Test
    void webhookShouldRejectInvalidToken() throws Exception {
        AsaasWebhookEvent event = new AsaasWebhookEvent("PAYMENT_CONFIRMED",
                new AsaasWebhookEvent.PaymentPayload("pay_123", "CONFIRMED", new BigDecimal("200.00"), null));

        mockMvc.perform(post("/api/v1/payments/webhook/asaas")
                        .header("asaas-access-token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookShouldAcceptValidTokenAndConfirmPayment() throws Exception {
        User client = createUser(UserRole.client);

        Payment payment = Payment.builder()
                .orderId(UUID.randomUUID())
                .payerUserId(client.getId())
                .receiverProfessionalId(UUID.randomUUID())
                .status(PaymentStatus.pending)
                .method(PaymentMethod.pix)
                .grossAmount(new BigDecimal("200.00"))
                .platformFee(new BigDecimal("40.00"))
                .netAmount(new BigDecimal("160.00"))
                .asaasPaymentId("pay_webhook_test")
                .build();
        paymentRepository.save(payment);

        // Cria transaction de charge correspondente
        PaymentTransaction chargeTx = PaymentTransaction.builder()
                .paymentId(payment.getId())
                .type(TransactionType.charge)
                .status(TransactionStatus.pending)
                .amount(new BigDecimal("200.00"))
                .asaasId("pay_webhook_test")
                .build();
        transactionRepository.save(chargeTx);

        AsaasWebhookEvent event = new AsaasWebhookEvent("PAYMENT_CONFIRMED",
                new AsaasWebhookEvent.PaymentPayload("pay_webhook_test", "CONFIRMED", new BigDecimal("200.00"), null));

        mockMvc.perform(post("/api/v1/payments/webhook/asaas")
                        .header("asaas-access-token", "test-webhook-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true));

        // Verifica que o pagamento foi confirmado
        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.confirmed);
        assertThat(updated.getPaidAt()).isNotNull();

        // Verifica que a transaction foi atualizada
        PaymentTransaction updatedTx = transactionRepository.findByAsaasId("pay_webhook_test").orElseThrow();
        assertThat(updatedTx.getStatus()).isEqualTo(TransactionStatus.confirmed);
        assertThat(updatedTx.getProcessedAt()).isNotNull();

        // Verifica audit log
        List<PaymentStatusHistory> history = historyRepository.findAll();
        assertThat(history).anyMatch(h ->
                h.getPaymentId().equals(payment.getId())
                        && h.getToStatus() == PaymentStatus.confirmed);
    }

    @Test
    void webhookShouldBeIdempotentWhenPaymentAlreadyConfirmed() throws Exception {
        User client = createUser(UserRole.client);

        Payment payment = Payment.builder()
                .orderId(UUID.randomUUID())
                .payerUserId(client.getId())
                .receiverProfessionalId(UUID.randomUUID())
                .status(PaymentStatus.confirmed)
                .method(PaymentMethod.pix)
                .grossAmount(new BigDecimal("200.00"))
                .platformFee(new BigDecimal("40.00"))
                .netAmount(new BigDecimal("160.00"))
                .asaasPaymentId("pay_idempotent")
                .paidAt(Instant.now())
                .build();
        paymentRepository.save(payment);

        AsaasWebhookEvent event = new AsaasWebhookEvent("PAYMENT_CONFIRMED",
                new AsaasWebhookEvent.PaymentPayload("pay_idempotent", "CONFIRMED", new BigDecimal("200.00"), null));

        mockMvc.perform(post("/api/v1/payments/webhook/asaas")
                        .header("asaas-access-token", "test-webhook-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk());

        // Status nao deve ter mudado
        Payment unchanged = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(PaymentStatus.confirmed);
    }

    // ─────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────

    private User createUser(UserRole role) {
        int current = sequence.getAndIncrement();
        String cpf = String.format("%011d", current);
        String cpfHash = ("%064d").formatted(current);

        User user = User.builder()
                .name("Usuario " + current)
                .cpf(cpf)
                .cpfHash(cpfHash)
                .email("user" + current + "@example.com")
                .phone("8599999000" + current)
                .password("senha-hash")
                .role(role)
                .build();

        return userRepository.save(user);
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(UUID userId, String role) {
        return jwt()
                .jwt(jwt -> jwt.subject(userId.toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
