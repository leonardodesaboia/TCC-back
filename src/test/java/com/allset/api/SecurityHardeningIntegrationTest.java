package com.allset.api;

import com.allset.api.address.domain.SavedAddress;
import com.allset.api.address.repository.SavedAddressRepository;
import com.allset.api.catalog.dto.CreateServiceAreaRequest;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.dto.UpdateGeoRequest;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.dto.CreateUserRequest;
import java.time.LocalDate;
import com.allset.api.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class SecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SavedAddressRepository savedAddressRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    private final AtomicInteger sequence = new AtomicInteger(1);

    @Test
    void shouldBlockCrossUserProfileAndAddressAccess() throws Exception {
        User attacker = createUser(UserRole.client);
        User victim = createUser(UserRole.client);
        createAddress(victim.getId());

        mockMvc.perform(get("/api/users/{id}", victim.getId())
                        .with(jwtFor(attacker.getId(), "client")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users/{userId}/addresses", victim.getId())
                        .with(jwtFor(attacker.getId(), "client")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRestrictAdminWritesAndProfessionalOwnership() throws Exception {
        User client = createUser(UserRole.client);
        User professionalUser = createUser(UserRole.professional);
        User otherProfessionalUser = createUser(UserRole.professional);
        Professional professional = createProfessional(professionalUser.getId());

        mockMvc.perform(post("/api/v1/service-areas")
                        .with(jwtFor(client.getId(), "client"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateServiceAreaRequest("Limpeza"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/professionals/{id}/geo", professional.getId())
                        .with(jwtFor(otherProfessionalUser.getId(), "professional"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateGeoRequest(
                                true,
                                new BigDecimal("-3.731862"),
                                new BigDecimal("-38.526669")
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectHtmlPayloadAndExposeSecurityHeaders() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                "<script>alert(1)</script>",
                                "52998224725",
                                "xss@example.com",
                                "+5585999999999",
                                LocalDate.of(1995, 9, 15),
                                "Senha@2025!",
                                UserRole.client
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

    private User createUser(UserRole role) {
        int current = sequence.getAndIncrement();
        String unique = UUID.randomUUID().toString().replace("-", "");
        return userRepository.save(User.builder()
                .name("Usuario " + current)
                .cpf(unique.substring(0, 11))
                .cpfHash((unique + unique).substring(0, 64))
                .email("security" + current + "-" + unique.substring(0, 8) + "@example.com")
                .phone("+55859999990" + current)
                .password("senha-hash")
                .role(role)
                .build());
    }

    private SavedAddress createAddress(UUID userId) {
        return savedAddressRepository.save(SavedAddress.builder()
                .userId(userId)
                .label("Casa")
                .street("Rua Teste")
                .number("100")
                .city("Fortaleza")
                .state("CE")
                .zipCode("60000-000")
                .isDefault(true)
                .build());
    }

    private Professional createProfessional(UUID userId) {
        return professionalRepository.save(Professional.builder()
                .userId(userId)
                .bio("Profissional experiente")
                .yearsOfExperience((short) 5)
                .baseHourlyRate(new BigDecimal("80.00"))
                .verificationStatus(VerificationStatus.approved)
                .build());
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(UUID userId, String role) {
        return jwt()
                .jwt(jwt -> jwt.subject(userId.toString()).claim("role", role))
                .authorities(new SimpleGrantedAuthority(role));
    }
}
