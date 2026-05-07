package com.allset.api.favorite.repository;

import com.allset.api.favorite.domain.FavoriteProfessional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FavoriteProfessionalRepository extends JpaRepository<FavoriteProfessional, UUID> {

    boolean existsByClientIdAndProfessionalId(UUID clientId, UUID professionalId);

    Optional<FavoriteProfessional> findByClientIdAndProfessionalId(UUID clientId, UUID professionalId);

    @Query("""
            select favorite
            from FavoriteProfessional favorite
            join Professional professional on professional.id = favorite.professionalId
            where favorite.clientId = :clientId
              and professional.deletedAt is null
            """)
    Page<FavoriteProfessional> findAllActiveByClientId(@Param("clientId") UUID clientId, Pageable pageable);
}
