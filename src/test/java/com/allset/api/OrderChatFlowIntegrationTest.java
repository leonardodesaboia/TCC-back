package com.allset.api;

import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.repository.SavedAddressRepository;
import com.allset.api.catalog.domain.ServiceArea;
import com.allset.api.catalog.domain.ServiceCategory;
import com.allset.api.catalog.repository.ServiceAreaRepository;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.chat.domain.Conversation;
import com.allset.api.chat.repository.ConversationRepository;
import com.allset.api.chat.repository.MessageRepository;
import com.allset.api.notification.domain.NotificationType;
import com.allset.api.notification.repository.NotificationRepository;
import com.allset.api.offering.domain.PricingType;
import com.allset.api.offering.domain.ProfessionalOffering;
import com.allset.api.offering.repository.ProfessionalOfferingRepository;
import com.allset.api.order.dto.ClientRespondRequest;
import com.allset.api.order.dto.CreateExpressOrderRequest;
import com.allset.api.order.dto.OrderResponse;
import com.allset.api.order.dto.ProRespondRequest;
import com.allset.api.order.domain.ProResponse;
import com.allset.api.order.repository.ExpressQueueRepository;
import com.allset.api.order.repository.OrderPhotoRepository;
import com.allset.api.order.repository.OrderRepository;
import com.allset.api.order.repository.OrderStatusHistoryRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.review.repository.ReviewRepository;
import com.allset.api.integration.storage.domain.StorageBucket;
import com.allset.api.integration.storage.domain.StoredObject;
import com.allset.api.integration.storage.service.StorageService;
import com.allset.api.shared.token.TokenService;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        "email-from=test@example.com",
        "minio.endpoint=http://test:9000",
        "minio.public-endpoint=http://test:9000",
        "minio.access-key=test",
        "minio.secret-key=testsecret",
        "minio.auto-create-buckets=false"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OrderChatFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private OrderPhotoRepository orderPhotoRepository;

    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private ExpressQueueRepository expressQueueRepository;

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

    @MockBean
    private StorageService storageService;

    private final AtomicInteger sequence = new AtomicInteger(1);

    @BeforeEach
    void cleanDatabase() {
        reviewRepository.deleteAll();
        notificationRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        orderPhotoRepository.deleteAll();
        orderStatusHistoryRepository.deleteAll();
        expressQueueRepository.deleteAll();
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
    void shouldExecuteExpressOrderFlowAndChatIntegration() throws Exception {
        User client = createUser(UserRole.client);
        User professionalUser = createUser(UserRole.professional);
        Professional professional = createProfessional(professionalUser.getId());
        ServiceArea area = createArea();
        ServiceCategory category = createCategory(area.getId());
        createOffering(professional.getId(), category.getId());
        SavedAddress address = createAddress(client.getId());

        String clientBearer = bearer(client.getId(), "client");
        String professionalBearer = bearer(professionalUser.getId(), "professional");

        OrderResponse order = createOrder(clientBearer, new CreateExpressOrderRequest(
                area.getId(),
                category.getId(),
                "Trocar tomada com urgencia",
                address.getId(),
                new BigDecimal("15.00")
        ));

        mockMvc.perform(post("/api/v1/orders/{id}/express/pro-respond", order.id())
                        .header("Authorization", professionalBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProRespondRequest(
                                ProResponse.accepted,
                                new BigDecimal("120.00")
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders/{id}/express/proposals", order.id())
                        .header("Authorization", clientBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].professionalId").value(professional.getId().toString()))
                .andExpect(jsonPath("$[0].proposedAmount").value(120.00));

        mockMvc.perform(post("/api/v1/orders/{id}/express/client-respond", order.id())
                        .header("Authorization", clientBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClientRespondRequest(professional.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.totalAmount").value(135.00));

        MvcResult conversationResult = mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", clientBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lastMessage.content").value("Pedido aceito. Vocês podem conversar por aqui."))
                .andReturn();

        JsonNode conversationPage = objectMapper.readTree(conversationResult.getResponse().getContentAsByteArray());
        UUID conversationId = UUID.fromString(conversationPage.get("content").get(0).get("id").asText());

        mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
                        .header("Authorization", clientBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Olá, consigo receber hoje?"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Olá, consigo receber hoje?"));

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                        .header("Authorization", professionalBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("Olá, consigo receber hoje?"));

        mockMvc.perform(patch("/api/v1/conversations/{id}/read", conversationId)
                        .header("Authorization", professionalBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(1));

        String completionKey = "order-photos/" + order.id() + "/completion.jpg";
        when(storageService.upload(eq(StorageBucket.ORDER_PHOTOS), eq(order.id().toString()), any(org.springframework.web.multipart.MultipartFile.class)))
                .thenReturn(new StoredObject(StorageBucket.ORDER_PHOTOS, completionKey, "image/jpeg", 4L));
        when(storageService.generateDownloadUrl(eq(StorageBucket.ORDER_PHOTOS), eq(completionKey)))
                .thenReturn("http://test/download/" + completionKey);

        MockMultipartFile completionPhoto = new MockMultipartFile(
                "file", "completion.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3, 4});
        mockMvc.perform(multipart("/api/v1/orders/{id}/complete", order.id())
                        .file(completionPhoto)
                        .header("Authorization", professionalBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed_by_pro"));

        mockMvc.perform(post("/api/v1/orders/{id}/confirm", order.id())
                        .header("Authorization", clientBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        mockMvc.perform(get("/api/v1/orders/{id}", order.id())
                        .header("Authorization", clientBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        assertThat(orderPhotoRepository.findAllByOrderId(order.id()))
                .extracting(p -> p.getStorageKey())
                .containsExactly(completionKey);
        assertThat(notificationRepository.findAll()).extracting(notification -> notification.getType())
                .contains(NotificationType.new_request, NotificationType.request_accepted,
                        NotificationType.new_message, NotificationType.request_status_update);
        assertThat(conversationRepository.findAll()).hasSize(1);
        assertThat(messageRepository.findAll()).hasSize(2);
        assertThat(orderStatusHistoryRepository.findAllByOrderIdOrderByCreatedAtAsc(order.id()))
                .extracting(history -> history.getToStatus().name())
                .containsExactly("pending", "accepted", "completed_by_pro", "completed");
    }

    private OrderResponse createOrder(String bearer, CreateExpressOrderRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders/express")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), OrderResponse.class);
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
                .phone("+55859999000" + current)
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
                .geoLat(new BigDecimal("-3.732000"))
                .geoLng(new BigDecimal("-38.527000"))
                .geoActive(true)
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

    private String bearer(UUID userId, String role) {
        return "Bearer " + tokenService.generateAccessToken(userId, role);
    }
}
