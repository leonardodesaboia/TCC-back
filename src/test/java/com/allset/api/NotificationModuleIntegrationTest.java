package com.allset.api;

import com.allset.api.notification.domain.Notification;
import com.allset.api.notification.domain.NotificationType;
import com.allset.api.notification.dto.RegisterPushTokenRequest;
import com.allset.api.notification.dto.UpdateNotificationPreferenceRequest;
import com.allset.api.notification.repository.NotificationRepository;
import com.allset.api.notification.repository.PushTokenRepository;
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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class NotificationModuleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    private final AtomicInteger sequence = new AtomicInteger(1);

    @BeforeEach
    void cleanDatabase() {
        notificationRepository.deleteAll();
        pushTokenRepository.deleteAll();
        userRepository.deleteAll();
        sequence.set(1);
    }

    @Test
    void shouldListNotificationsForAuthenticatedUser() throws Exception {
        User user = createUser(UserRole.client);

        notificationRepository.save(Notification.builder()
                .userId(user.getId())
                .type(NotificationType.new_message)
                .title("Nova mensagem")
                .body("Voce recebeu uma mensagem.")
                .createdAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/notifications")
                        .with(jwtFor(user.getId(), "client")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Nova mensagem"))
                .andExpect(jsonPath("$.content[0].type").value("new_message"));
    }

    @Test
    void shouldUpdatePreferenceAndRegisterPushToken() throws Exception {
        User user = createUser(UserRole.client);

        mockMvc.perform(patch("/api/v1/notifications/preferences")
                        .with(jwtFor(user.getId(), "client"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateNotificationPreferenceRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationsEnabled").value(false));

        user = userRepository.findById(user.getId()).orElseThrow();
        assertThat(user.isNotificationsEnabled()).isFalse();

        mockMvc.perform(post("/api/v1/push-tokens")
                        .with(jwtFor(user.getId(), "client"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterPushTokenRequest("ExponentPushToken[token-1]", com.allset.api.notification.domain.Platform.android))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expoToken").value("ExponentPushToken[token-1]"))
                .andExpect(jsonPath("$.platform").value("android"));

        mockMvc.perform(get("/api/v1/push-tokens")
                        .with(jwtFor(user.getId(), "client")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expoToken").value("ExponentPushToken[token-1]"));
    }

    @Test
    void shouldMarkNotificationAsRead() throws Exception {
        User user = createUser(UserRole.client);

        Notification notification = notificationRepository.save(Notification.builder()
                .userId(user.getId())
                .type(NotificationType.request_status_update)
                .title("Atualizacao")
                .body("Pedido aceito.")
                .createdAt(Instant.now())
                .build());

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notification.getId())
                        .with(jwtFor(user.getId(), "client")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notification.getId().toString()))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(updated.getReadAt()).isNotNull();
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

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(UUID userId, String role) {
        return jwt()
                .jwt(jwt -> jwt.subject(userId.toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
