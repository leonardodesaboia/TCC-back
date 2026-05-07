package com.allset.api.order.repository;

import com.allset.api.order.domain.ClientResponse;
import com.allset.api.order.domain.ExpressQueueEntry;
import com.allset.api.order.domain.ProResponse;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpressQueueRepository extends JpaRepository<ExpressQueueEntry, UUID> {

    Optional<ExpressQueueEntry> findByOrderIdAndProfessionalId(UUID orderId, UUID professionalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT e FROM ExpressQueueEntry e WHERE e.orderId = :orderId AND e.professionalId = :professionalId")
    Optional<ExpressQueueEntry> findByOrderIdAndProfessionalIdForUpdate(
            @Param("orderId") UUID orderId, @Param("professionalId") UUID professionalId);

    List<ExpressQueueEntry> findAllByOrderIdOrderByQueuePositionAsc(UUID orderId);

    List<ExpressQueueEntry> findAllByOrderIdAndProResponse(UUID orderId, ProResponse proResponse);

    boolean existsByOrderIdAndProResponse(UUID orderId, ProResponse proResponse);

    boolean existsByOrderIdAndProResponseIsNull(UUID orderId);

    long countByOrderIdAndProResponse(UUID orderId, ProResponse proResponse);

    /**
     * Rejeita em lote todas as propostas aceitas de um pedido, exceto a escolhida pelo cliente.
     */
    @Modifying
    @Query("""
        UPDATE ExpressQueueEntry e
        SET e.clientResponse = :response,
            e.clientRespondedAt = :now
        WHERE e.orderId = :orderId
          AND e.proResponse = :acceptedResponse
          AND e.id <> :chosenEntryId
        """)
    void rejectOtherProposals(
            @Param("orderId") UUID orderId,
            @Param("chosenEntryId") UUID chosenEntryId,
            @Param("acceptedResponse") ProResponse acceptedResponse,
            @Param("response") ClientResponse response,
            @Param("now") Instant now
    );

    /**
     * Profissionais próximos via Haversine, em metros, com snapshot da distância.
     * Retorna pares (professionalId, distanceMeters) ordenados por: assinantes Pro primeiro,
     * depois proximidade. Limitado a maxSize.
     * Filtra por professional_specialties — elegibilidade independente de ofertas publicadas.
     */
    @Query(value = """
        SELECT p.id AS professional_id, ROUND(
            6371000 * acos(
                GREATEST(-1.0, LEAST(1.0,
                    cos(radians(:lat)) * cos(radians(p.geo_lat))
                    * cos(radians(p.geo_lng) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(p.geo_lat))
                ))
            )
        )::int AS distance_meters
        FROM professionals p
        WHERE p.geo_active = TRUE
          AND p.verification_status = 'approved'
          AND p.deleted_at IS NULL
          AND EXISTS (
              SELECT 1 FROM professional_specialties ps
              WHERE ps.professional_id = p.id
                AND ps.category_id = :categoryId
                AND ps.deleted_at IS NULL
          )
          AND (
              6371000 * acos(
                  GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.geo_lat))
                      * cos(radians(p.geo_lng) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.geo_lat))
                  ))
              )
          ) <= :radiusMeters
        ORDER BY
          COALESCE((
              SELECT sp.express_priority
              FROM subscription_plans sp
              WHERE sp.id = p.subscription_plan_id
                AND sp.deleted_at IS NULL
                AND p.subscription_expires_at > NOW()
          ), FALSE) DESC,
          distance_meters ASC
        LIMIT :maxSize
        """, nativeQuery = true)
    List<NearbyProfessional> findNearbyProfessionals(
            @Param("categoryId") UUID categoryId,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") int radiusMeters,
            @Param("maxSize") int maxSize
    );

    interface NearbyProfessional {
        UUID getProfessionalId();
        int getDistanceMeters();
    }

    @Modifying
    @Query(value = """
        UPDATE express_queue
        SET pro_response = 'timeout',
            responded_at = :now
        WHERE order_id = :orderId
          AND pro_response IS NULL
        """, nativeQuery = true)
    int markPendingEntriesAsTimeout(@Param("orderId") UUID orderId, @Param("now") Instant now);
}
