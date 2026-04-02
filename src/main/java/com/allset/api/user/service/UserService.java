package com.allset.api.user.service;

import com.allset.api.user.dto.BanUserRequest;
import com.allset.api.user.dto.CreateUserRequest;
import com.allset.api.user.dto.UpdateUserRequest;
import com.allset.api.user.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    UserResponse create(CreateUserRequest request);

    UserResponse findById(UUID id);

    Page<UserResponse> findAll(boolean banned, boolean deleted, Pageable pageable);

    UserResponse update(UUID id, UpdateUserRequest request);

    UserResponse softDelete(UUID id);

    UserResponse reactivate(UUID id);

    UserResponse ban(UUID id, BanUserRequest request);

    UserResponse activate(UUID id);

    /**
     * Atualiza a senha de um usuário com o hash já codificado (BCrypt).
     * Usado exclusivamente pelo fluxo de redefinição de senha via código de e-mail.
     *
     * @param id              UUID do usuário
     * @param encodedPassword senha já codificada com BCrypt
     */
    void updatePassword(UUID id, String encodedPassword);
}
