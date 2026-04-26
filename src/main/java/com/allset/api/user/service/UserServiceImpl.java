package com.allset.api.user.service;

import com.allset.api.shared.storage.domain.StorageBucket;
import com.allset.api.shared.storage.domain.StoredObject;
import com.allset.api.shared.storage.event.ObjectDeletionRequestedEvent;
import com.allset.api.shared.storage.service.StorageService;
import com.allset.api.user.domain.User;
import com.allset.api.user.dto.BanUserRequest;
import com.allset.api.user.dto.CreateUserRequest;
import com.allset.api.user.dto.UpdateUserRequest;
import com.allset.api.user.dto.UserResponse;
import com.allset.api.user.exception.CpfAlreadyExistsException;
import com.allset.api.user.exception.EmailAlreadyExistsException;
import com.allset.api.user.exception.UserNotFoundException;
import com.allset.api.user.exception.UserPendingDeletionException;
import com.allset.api.user.mapper.UserMapper;
import com.allset.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public UserResponse create(CreateUserRequest request) {
        userRepository.findByEmailAndDeletedAtIsNotNull(request.email())
            .ifPresent(u -> { throw new UserPendingDeletionException(u.getDeletedAt().plus(30, ChronoUnit.DAYS)); });

        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        String cpfHash = sha256Hex(request.cpf());

        userRepository.findByCpfHashAndDeletedAtIsNotNull(cpfHash)
            .ifPresent(u -> { throw new UserPendingDeletionException(u.getDeletedAt().plus(30, ChronoUnit.DAYS)); });

        if (userRepository.existsByCpfHash(cpfHash)) {
            throw new CpfAlreadyExistsException();
        }

        User user = User.builder()
            .name(request.name())
            .cpf(request.cpf())
            .cpfHash(cpfHash)
            .email(request.email())
            .phone(request.phone())
            .password(passwordEncoder.encode(request.password()))
            .role(request.role())
            .build();

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        User user = findActiveById(id);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(boolean banned, boolean deleted, Pageable pageable) {
        if (deleted) {
            return userRepository.findAllByDeletedAtIsNotNull(pageable).map(userMapper::toResponse);
        }
        if (banned) {
            return userRepository.findAllByActiveFalseAndDeletedAtIsNull(pageable).map(userMapper::toResponse);
        }
        return userRepository.findAllByDeletedAtIsNull(pageable).map(userMapper::toResponse);
    }

    @Override
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findActiveById(id);

        if (request.name() != null) {
            user.setName(request.name());
        }
        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new EmailAlreadyExistsException(request.email());
            }
            user.setEmail(request.email());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse uploadAvatar(UUID id, MultipartFile file) {
        User user = findActiveById(id);
        String previousKey = user.getAvatarUrl();

        StoredObject stored = storageService.upload(StorageBucket.AVATARS, user.getId().toString(), file);
        user.setAvatarUrl(stored.key());
        User saved = userRepository.save(user);

        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(stored.key())) {
            eventPublisher.publishEvent(new ObjectDeletionRequestedEvent(StorageBucket.AVATARS, previousKey));
        }
        return userMapper.toResponse(saved);
    }

    @Override
    public UserResponse deleteAvatar(UUID id) {
        User user = findActiveById(id);
        String previousKey = user.getAvatarUrl();
        if (previousKey == null || previousKey.isBlank()) {
            return userMapper.toResponse(user);
        }
        user.setAvatarUrl(null);
        User saved = userRepository.save(user);
        eventPublisher.publishEvent(new ObjectDeletionRequestedEvent(StorageBucket.AVATARS, previousKey));
        return userMapper.toResponse(saved);
    }

    @Override
    public UserResponse softDelete(UUID id) {
        User user = findActiveById(id);
        user.setDeletedAt(Instant.now());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse reactivate(UUID id) {
        User user = userRepository.findByIdAndDeletedAtIsNotNull(id)
            .orElseThrow(() -> new UserNotFoundException(id));
        user.setDeletedAt(null);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse ban(UUID id, BanUserRequest request) {
        User user = findActiveById(id);
        user.setActive(false);
        user.setBanReason(request.reason());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse activate(UUID id) {
        User user = findActiveById(id);
        user.setActive(true);
        user.setBanReason(null);
        return userMapper.toResponse(userRepository.save(user));
    }

    private User findActiveById(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    public void updatePassword(UUID id, String encodedPassword) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new UserNotFoundException(id));
        user.setPassword(encodedPassword);
        userRepository.save(user);
    }

    // TODO(auth): no fluxo de login, após localizar o usuário pelo e-mail, verificar se
    // deletedAt != null antes de validar a senha. Se estiver em período de graça, lançar
    // UserPendingDeletionException(deletedAt.plus(30, ChronoUnit.DAYS)) — o frontend
    // recebe 423 e redireciona para a tela de reativação de conta.

    private static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível.", e);
        }
    }
}
