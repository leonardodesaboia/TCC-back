package com.allset.api;

import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.repository.SavedAddressRepository;
import com.allset.api.catalog.domain.ServiceArea;
import com.allset.api.catalog.domain.ServiceCategory;
import com.allset.api.catalog.repository.ServiceAreaRepository;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.offering.domain.PricingType;
import com.allset.api.offering.domain.ProfessionalOffering;
import com.allset.api.offering.repository.ProfessionalOfferingRepository;
import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderMode;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.repository.OrderRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.review.dto.CreateReviewRequest;
import com.allset.api.review.repository.ReviewRepository;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        "review-publication-cron=0 0 * * * *",
        "resend-api-key=test-key",
        "email-from=test@example.com"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ReviewModuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProfessionalOfferingRepository professionalOfferingRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private SavedAddressRepository savedAddressRepository;

    @Autowired
    private ServiceCategoryRepository serviceCategoryRepository;

    @Autowired
    private ServiceAreaRepository serviceAreaRepository;

    @Autowired
    private UserRepository userRepository;

    private final AtomicInteger sequence = new AtomicInteger(1);

    @BeforeEach
    void cleanDatabase() {
        reviewRepository.deleteAll();
        orderRepository.deleteAll();
        professionalOfferingRepository.deleteAll();
        professionalRepository.deleteAll();
        savedAddressRepository.deleteAll();
        serviceCategoryRepository.deleteAll();
        serviceAreaRepository.deleteAll();
        userRepository.deleteAll();
        sequence.set(1);
    }

    @Test
    void shouldCreateAndPublishDoubleBlindReviewsAndExposeAverages() throws Exception {
        User client = createUser(UserRole.client);
        User professionalUser = createUser(UserRole.professional);
        Professional professional = createProfessional(professionalUser.getId());
        ServiceArea area = createArea();
        ServiceCategory category = createCategory(area.getId());
        ProfessionalOffering offering = createOffering(professional.getId(), category.getId());
        SavedAddress address = createAddress(client.getId());
        Order order = createCompletedOrder(client.getId(), professional.getId(), offering.getId(), area.getId(), category.getId(), address.getId());

        mockMvc.perform(post("/api/v1/orders/{orderId}/reviews", order.getId())
                        .with(jwtFor(client.getId(), "client"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest((short) 5, "Servico excelente"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Servico excelente"))
                .andExpect(jsonPath("$.publishedAt").isEmpty());

        mockMvc.perform(post("/api/v1/orders/{orderId}/reviews", order.getId())
                        .with(jwtFor(professionalUser.getId(), "professional"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest((short) 4, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.comment").isEmpty())
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/orders/{orderId}/reviews", order.getId())
                        .with(jwtFor(client.getId(), "client")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[1].rating").value(4));

        mockMvc.perform(get("/api/v1/professionals/{id}", professional.getId())
                        .with(jwtFor(client.getId(), "client")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.00))
                .andExpect(jsonPath("$.reviewCount").value(1));

        mockMvc.perform(get("/api/v1/professionals/{professionalId}/services/{serviceId}", professional.getId(), offering.getId())
                        .with(jwtFor(client.getId(), "client")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.00))
                .andExpect(jsonPath("$.reviewCount").value(1));

        mockMvc.perform(get("/api/users/{id}", client.getId())
                        .with(jwtFor(professionalUser.getId(), "professional")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.00))
                .andExpect(jsonPath("$.reviewCount").value(1));

        assertThat(reviewRepository.findAll()).hasSize(2);
        assertThat(reviewRepository.findAll()).allMatch(review -> review.getPublishedAt() != null);
    }

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

    private Professional createProfessional(UUID userId) {
        Professional professional = Professional.builder()
                .userId(userId)
                .bio("Profissional experiente")
                .yearsOfExperience((short) 8)
                .baseHourlyRate(new BigDecimal("80.00"))
                .verificationStatus(VerificationStatus.approved)
                .build();

        return professionalRepository.save(professional);
    }

    private ServiceArea createArea() {
        return serviceAreaRepository.save(ServiceArea.builder()
                .name("Eletrica " + sequence.getAndIncrement())
                .active(true)
                .build());
    }

    private ServiceCategory createCategory(UUID areaId) {
        return serviceCategoryRepository.save(ServiceCategory.builder()
                .areaId(areaId)
                .name("Instalacao " + sequence.getAndIncrement())
                .active(true)
                .build());
    }

    private ProfessionalOffering createOffering(UUID professionalId, UUID categoryId) {
        return professionalOfferingRepository.save(ProfessionalOffering.builder()
                .professionalId(professionalId)
                .categoryId(categoryId)
                .title("Instalacao de tomada")
                .description("Servico rapido")
                .pricingType(PricingType.fixed)
                .price(new BigDecimal("150.00"))
                .estimatedDurationMinutes(90)
                .active(true)
                .build());
    }

    private SavedAddress createAddress(UUID userId) {
        return savedAddressRepository.save(SavedAddress.builder()
                .userId(userId)
                .label("Casa")
                .street("Rua Teste")
                .number("100")
                .district("Centro")
                .city("Fortaleza")
                .state("CE")
                .zipCode("60000-000")
                .lat(new BigDecimal("-3.731862"))
                .lng(new BigDecimal("-38.526669"))
                .isDefault(true)
                .build());
    }

    private Order createCompletedOrder(
            UUID clientId,
            UUID professionalId,
            UUID serviceId,
            UUID areaId,
            UUID categoryId,
            UUID addressId
    ) {
        ObjectNode addressSnapshot = JsonNodeFactory.instance.objectNode();
        addressSnapshot.put("street", "Rua Teste");
        addressSnapshot.put("city", "Fortaleza");
        addressSnapshot.put("state", "CE");

        Order order = Order.builder()
                .clientId(clientId)
                .professionalId(professionalId)
                .serviceId(serviceId)
                .areaId(areaId)
                .categoryId(categoryId)
                .mode(OrderMode.on_demand)
                .status(OrderStatus.completed)
                .description("Trocar tomada")
                .addressId(addressId)
                .addressSnapshot(addressSnapshot)
                .scheduledAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600))
                .baseAmount(new BigDecimal("150.00"))
                .platformFee(new BigDecimal("30.00"))
                .totalAmount(new BigDecimal("150.00"))
                .completedAt(Instant.now().minusSeconds(1800))
                .build();

        return orderRepository.save(order);
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(UUID userId, String role) {
        return jwt()
                .jwt(jwt -> jwt.subject(userId.toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
