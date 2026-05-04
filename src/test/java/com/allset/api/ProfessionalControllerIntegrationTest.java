package com.allset.api;

import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.shared.token.TokenService;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ProfessionalControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private UserRepository userRepository;

    private final AtomicInteger sequence = new AtomicInteger(1);

    @BeforeEach
    void cleanDatabase() {
        professionalRepository.deleteAll();
        userRepository.deleteAll();
        sequence.set(1);
    }

    @Test
    void shouldReturnAuthenticatedProfessionalProfileFromMeEndpoint() throws Exception {
        User professionalUser = createUser(UserRole.professional);
        Professional professional = createProfessional(professionalUser.getId());

        mockMvc.perform(get("/api/v1/professionals/me")
                        .header("Authorization", bearer(professionalUser.getId(), "professional")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(professional.getId().toString()))
                .andExpect(jsonPath("$.userId").value(professionalUser.getId().toString()))
                .andExpect(jsonPath("$.bio").value("Profissional de teste"));
    }

    @Test
    void shouldReturnNotFoundWhenAuthenticatedUserHasNoProfessionalProfile() throws Exception {
        User professionalUserWithoutProfile = createUser(UserRole.professional);

        mockMvc.perform(get("/api/v1/professionals/me")
                        .header("Authorization", bearer(professionalUserWithoutProfile.getId(), "professional")))
                .andExpect(status().isNotFound());
    }

    private User createUser(UserRole role) {
        int current = sequence.getAndIncrement();
        User user = User.builder()
                .name("Usuario " + current)
                .cpf(String.format("%011d", current))
                .cpfHash("%064d".formatted(current))
                .email("user" + current + "@example.com")
                .phone("8599999999" + current)
                .password("senha-hash")
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private Professional createProfessional(UUID userId) {
        Professional professional = Professional.builder()
                .userId(userId)
                .bio("Profissional de teste")
                .yearsOfExperience((short) 6)
                .baseHourlyRate(new BigDecimal("90.00"))
                .build();
        return professionalRepository.save(professional);
    }

    private String bearer(UUID userId, String role) {
        return "Bearer " + tokenService.generateAccessToken(userId, role);
    }
}
