package com.allset.api.address.service;

import com.allset.api.address.dto.CreateSavedAddressRequest;
import com.allset.api.address.dto.SavedAddressResponse;
import com.allset.api.address.dto.UpdateSavedAddressRequest;

import java.util.List;
import java.util.UUID;

public interface SavedAddressService {

    /**
     * Cria um endereço para o usuário informado.
     * Se {@code request.isDefault()} for {@code true}, desmarca os demais endereços do usuário.
     *
     * @throws com.allset.api.user.exception.UserNotFoundException se o usuário não existir
     */
    SavedAddressResponse create(UUID userId, CreateSavedAddressRequest request);

    /**
     * Retorna todos os endereços do usuário.
     *
     * @throws com.allset.api.user.exception.UserNotFoundException se o usuário não existir
     */
    List<SavedAddressResponse> findAllByUser(UUID userId);

    /**
     * Retorna um endereço pelo ID, verificando que pertence ao usuário.
     *
     * @throws com.allset.api.address.exception.SavedAddressNotFoundException se não encontrado ou não pertencer ao usuário
     */
    SavedAddressResponse findByIdAndUser(UUID userId, UUID id);

    /**
     * Atualiza os campos não nulos do endereço.
     * Se {@code request.isDefault()} for {@code true}, desmarca os demais endereços do usuário.
     *
     * @throws com.allset.api.address.exception.SavedAddressNotFoundException se não encontrado ou não pertencer ao usuário
     */
    SavedAddressResponse update(UUID userId, UUID id, UpdateSavedAddressRequest request);

    /**
     * Remove fisicamente o endereço.
     *
     * @throws com.allset.api.address.exception.SavedAddressNotFoundException se não encontrado ou não pertencer ao usuário
     */
    void delete(UUID userId, UUID id);

    /**
     * Marca o endereço como padrão e desmarca todos os outros do usuário.
     *
     * @throws com.allset.api.address.exception.SavedAddressNotFoundException se não encontrado ou não pertencer ao usuário
     */
    SavedAddressResponse setDefault(UUID userId, UUID id);
}
