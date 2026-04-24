package com.allset.api.user.service;

import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.dto.BanUserRequest;
import com.allset.api.user.dto.CreateUserRequest;
import com.allset.api.user.dto.UpdateUserRequest;
import com.allset.api.user.dto.UserResponse;
import com.allset.api.user.exception.EmailAlreadyExistsException;
import com.allset.api.user.mapper.UserMapper;
import com.allset.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void createShouldEncodePasswordAndPersistCpfHash() {
        UUID userId = UUID.randomUUID();
        CreateUserRequest request = new CreateUserRequest(
                "Maria Silva",
                "52998224725",
                "maria@example.com",
                "+5585999999999",
                "Senha@2025!",
                UserRole.client
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByCpfHash(sha256Hex(request.cpf()))).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("senha-criptografada");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(userId);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        when(userMapper.toResponse(any(User.class))).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

        UserResponse response = userService.create(request);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("maria@example.com");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("senha-criptografada");
        assertThat(captor.getValue().getCpfHash()).isEqualTo(sha256Hex("52998224725"));
    }

    @Test
    void updateShouldRejectDuplicatedEmail() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "maria@example.com");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.update(userId,
                new UpdateUserRequest("Maria Souza", "novo@example.com", null, null)))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("novo@example.com");
    }

    @Test
    void softDeleteShouldPersistDeletionTimestamp() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "maria@example.com");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

        userService.softDelete(userId);

        assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    void activateShouldClearBanReasonAndReactivateUser() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "maria@example.com");
        user.setActive(false);
        user.setBanReason("Descumpriu regras");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

        UserResponse response = userService.activate(userId);

        assertThat(response.active()).isTrue();
        assertThat(user.getBanReason()).isNull();
    }

    @Test
    void banShouldStoreReasonAndDeactivateUser() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, "maria@example.com");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

        UserResponse response = userService.ban(userId, new BanUserRequest("Violou os termos"));

        assertThat(response.active()).isFalse();
        assertThat(user.getBanReason()).isEqualTo("Violou os termos");
    }

    private User user(UUID id, String email) {
        User user = User.builder()
                .name("Maria Silva")
                .cpf("52998224725")
                .cpfHash(sha256Hex("52998224725"))
                .email(email)
                .phone("+5585999999999")
                .password("senha-criptografada")
                .role(UserRole.client)
                .active(true)
                .build();
        user.setId(id);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    private UserResponse toResponse(User user) {
        Instant scheduledDeletionAt = user.getDeletedAt() != null ? user.getDeletedAt().plusSeconds(30L * 24 * 60 * 60) : null;
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getAvatarUrl(),
                user.isActive(),
                user.getBanReason(),
                null,
                0L,
                user.getCreatedAt(),
                user.getUpdatedAt(),
                scheduledDeletionAt
        );
    }

    private String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
