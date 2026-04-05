package com.allset.api.user.repository;

import com.allset.api.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    boolean existsByCpfHash(String cpfHash);

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    // Usuários banidos (ativos=false, não deletados)
    Page<User> findAllByActiveFalseAndDeletedAtIsNull(Pageable pageable);

    // Usuários com soft delete
    Page<User> findAllByDeletedAtIsNotNull(Pageable pageable);

    Optional<User> findByEmailAndDeletedAtIsNotNull(String email);

    Optional<User> findByCpfHashAndDeletedAtIsNotNull(String cpfHash);

    Optional<User> findByIdAndDeletedAtIsNotNull(UUID id);

    // Usuários cujo período de graça já expirou
    List<User> findAllByDeletedAtIsNotNullAndDeletedAtBefore(Instant cutoff);
}
