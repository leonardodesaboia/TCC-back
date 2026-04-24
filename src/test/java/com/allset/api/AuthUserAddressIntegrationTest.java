package com.allset.api;

import com.allset.api.address.dto.CreateSavedAddressRequest;
import com.allset.api.address.dto.SavedAddressResponse;
import com.allset.api.address.dto.UpdateSavedAddressRequest;
import com.allset.api.auth.dto.ForgotPasswordRequest;
import com.allset.api.auth.dto.LoginRequest;
import com.allset.api.auth.dto.RefreshTokenRequest;
import com.allset.api.auth.dto.ResetPasswordRequest;
import com.allset.api.auth.dto.TokenResponse;
import com.allset.api.shared.cache.CacheService;
import com.allset.api.shared.email.EmailService;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.dto.CreateUserRequest;
import com.allset.api.user.dto.UserResponse;
import com.allset.api.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class AuthUserAddressIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.allset.api.address.repository.SavedAddressRepository savedAddressRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CacheService cacheService;

    @MockBean
    private EmailService emailService;

    @BeforeEach
    void cleanDatabase() {
        savedAddressRepository.deleteAll();
        userRepository.deleteAll();
        clearRedis();
    }

    @Test
    void shouldCreateUserLoginRefreshLogoutAndManageAddresses() throws Exception {
        UserResponse user = createUserViaApi(new CreateUserRequest(
                "Maria Silva",
                "52998224725",
                "maria@example.com",
                "+5585999999999",
                "Senha@2025!",
                UserRole.client
        ));

        TokenResponse login = login("maria@example.com", "Senha@2025!");
        String bearer = bearer(login.accessToken());

        mockMvc.perform(get("/api/users/{id}", user.id())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("maria@example.com"));

        SavedAddressResponse firstAddress = createAddress(user.id(), bearer, new CreateSavedAddressRequest(
                "Casa",
                "Rua A",
                "100",
                null,
                "Centro",
                "Fortaleza",
                "CE",
                "60000-000",
                new BigDecimal("-3.731862"),
                new BigDecimal("-38.526669"),
                true
        ));

        SavedAddressResponse secondAddress = createAddress(user.id(), bearer, new CreateSavedAddressRequest(
                "Trabalho",
                "Rua B",
                "200",
                null,
                "Aldeota",
                "Fortaleza",
                "CE",
                "60150-160",
                new BigDecimal("-3.735000"),
                new BigDecimal("-38.510000"),
                false
        ));

        MvcResult defaultResult = mockMvc.perform(patch("/api/users/{userId}/addresses/{id}/set-default", user.id(), secondAddress.id())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();

        SavedAddressResponse defaultAddress = objectMapper.readValue(
                defaultResult.getResponse().getContentAsByteArray(),
                SavedAddressResponse.class
        );
        assertThat(defaultAddress.id()).isEqualTo(secondAddress.id());
        assertThat(defaultAddress.isDefault()).isTrue();

        mockMvc.perform(put("/api/users/{userId}/addresses/{id}", user.id(), secondAddress.id())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateSavedAddressRequest(
                                null,
                                null,
                                null,
                                null,
                                null,
                                "Caucaia",
                                null,
                                null,
                                null,
                                null,
                                null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Caucaia"));

        MvcResult listResult = mockMvc.perform(get("/api/users/{userId}/addresses", user.id())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();

        SavedAddressResponse[] addresses = objectMapper.readValue(
                listResult.getResponse().getContentAsByteArray(),
                SavedAddressResponse[].class
        );

        assertThat(addresses).hasSize(2);
        assertThat(addresses).anyMatch(address -> address.id().equals(secondAddress.id()) && address.isDefault());
        assertThat(addresses).anyMatch(address -> address.id().equals(firstAddress.id()) && !address.isDefault());

        mockMvc.perform(delete("/api/users/{userId}/addresses/{id}", user.id(), firstAddress.id())
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        TokenResponse refreshed = refresh(login.refreshToken());
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotBlank();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshed.refreshToken()))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshed.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldResetPasswordAndAllowLoginWithNewPassword() throws Exception {
        User user = userRepository.save(User.builder()
                .name("Felipe")
                .cpf("11144477735")
                .cpfHash("b".repeat(64))
                .email("felipe@example.com")
                .phone("+5585988887777")
                .password(passwordEncoder.encode("Senha@2025!"))
                .role(UserRole.client)
                .build());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(user.getEmail()))))
                .andExpect(status().isNoContent());

        String storedCode = stringRedisTemplate.opsForValue().get("reset_code:" + user.getEmail());
        assertThat(storedCode).isNotBlank();
        verify(emailService).sendPasswordResetCode(eq(user.getEmail()), eq(storedCode), eq(10));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordRequest(
                                user.getEmail(),
                                storedCode,
                                "NovaSenha@2025!"
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), "Senha@2025!"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), "NovaSenha@2025!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        assertThat(cacheService.get("reset_code:" + user.getEmail())).isEmpty();
    }

    private UserResponse createUserViaApi(CreateUserRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), UserResponse.class);
    }

    private SavedAddressResponse createAddress(java.util.UUID userId, String bearer, CreateSavedAddressRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/{userId}/addresses", userId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), SavedAddressResponse.class);
    }

    private TokenResponse login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), TokenResponse.class);
    }

    private TokenResponse refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), TokenResponse.class);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private void clearRedis() {
        Set<String> keys = stringRedisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
