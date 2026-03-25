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

    void softDelete(UUID id);

    UserResponse ban(UUID id, BanUserRequest request);

    UserResponse activate(UUID id);
}
