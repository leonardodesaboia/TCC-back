package com.allset.api.address.repository;

import com.allset.api.address.domain.SavedAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedAddressRepository extends JpaRepository<SavedAddress, UUID> {

    /**
     * Retorna todos os endereços de um usuário.
     * Retorna List (não Page) pois a contagem de endereços por usuário é pequena.
     */
    List<SavedAddress> findAllByUserId(UUID userId);

    /**
     * Busca um endereço pelo ID e verifica ownership em um único round-trip.
     * Retorna vazio se o endereço não existir ou não pertencer ao userId informado.
     */
    Optional<SavedAddress> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Desmarca todos os endereços padrão de um usuário via bulk update JPQL.
     * Deve ser chamado dentro de uma transação ativa antes de setar o novo padrão,
     * garantindo a invariante "apenas um is_default=true por usuário".
     */
    @Modifying
    @Query("UPDATE SavedAddress a SET a.isDefault = false WHERE a.userId = :userId")
    void unsetDefaultForUser(@Param("userId") UUID userId);
}
