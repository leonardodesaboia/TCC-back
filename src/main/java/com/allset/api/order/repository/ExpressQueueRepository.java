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

    /** Verifica se ainda há profissionais sem resposta na fila (fase de propostas em aberto). */
    boolean existsByOrderIdAndProResponseIsNull(UUID orderId);

    long countByOrderIdAndProResponse(UUID orderId, ProResponse proResponse);

    /** IDs dos profissionais já presentes na fila de um pedido (para exclusão na expansão do raio). */
    @Query("SELECT e.professionalId FROM ExpressQueueEntry e WHERE e.orderId = :orderId")
    List<UUID> findProfessionalIdsByOrderId(@Param("orderId") UUID orderId);

    /**
     * Entries com timeout do profissional: notificados antes do cutoff sem ter respondido.
     * Usados pelo scheduler de timeout.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
        SELECT e FROM ExpressQueueEntry e
        WHERE e.proResponse IS NULL
          AND e.respondedAt IS NULL
          AND e.notifiedAt <= :cutoff
        """)
    List<ExpressQueueEntry> findTimedOutProEntries(@Param("cutoff") Instant cutoff);

    /**
     * Rejeita em lote todas as propostas aceitas de um pedido, exceto a escolhida pelo cliente.
     * Chamado após o cliente selecionar uma proposta para invalidar as demais.
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
     * Busca profissionais próximos via fórmula de Haversine.
     * Retorna IDs de profissionais aprovados, com geo ativo, que oferecem
     * a categoria solicitada, ordenados por: express_priority DESC, distância ASC.
     */
    @Query(value = """
        SELECT p.id FROM professionals p
        WHERE p.geo_active = TRUE
          AND p.verification_status = 'approved'
          AND p.deleted_at IS NULL
          AND EXISTS (
              SELECT 1 FROM professional_services ps
              WHERE ps.professional_id = p.id
                AND ps.category_id = :categoryId
                AND ps.is_active = TRUE
                AND ps.deleted_at IS NULL
          )
          AND (
              6371 * acos(
                  GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.geo_lat))
                      * cos(radians(p.geo_lng) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.geo_lat))
                  ))
              )
          ) <= :radiusKm
        ORDER BY
          COALESCE((
              SELECT sp.express_priority
              FROM subscription_plans sp
              WHERE sp.id = p.subscription_plan_id
                AND sp.deleted_at IS NULL
                AND p.subscription_expires_at > NOW()
          ), FALSE) DESC,
          (
              6371 * acos(
                  GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.geo_lat))
                      * cos(radians(p.geo_lng) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.geo_lat))
                  ))
              )
          ) ASC
        LIMIT :maxSize
        """, nativeQuery = true)
    List<UUID> findNearbyProfessionalIds(
            @Param("categoryId") UUID categoryId,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm,
            @Param("maxSize") int maxSize
    );

    /**
     * Igual ao anterior, mas exclui profissionais já presentes na fila.
     * Usado na expansão de raio para evitar notificar o mesmo profissional duas vezes.
     */
    @Query(value = """
        SELECT p.id FROM professionals p
        WHERE p.geo_active = TRUE
          AND p.verification_status = 'approved'
          AND p.deleted_at IS NULL
          AND p.id NOT IN :excludeIds
          AND EXISTS (
              SELECT 1 FROM professional_services ps
              WHERE ps.professional_id = p.id
                AND ps.category_id = :categoryId
                AND ps.is_active = TRUE
                AND ps.deleted_at IS NULL
          )
          AND (
              6371 * acos(
                  GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.geo_lat))
                      * cos(radians(p.geo_lng) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.geo_lat))
                  ))
              )
          ) <= :radiusKm
        ORDER BY
          COALESCE((
              SELECT sp.express_priority
              FROM subscription_plans sp
              WHERE sp.id = p.subscription_plan_id
                AND sp.deleted_at IS NULL
                AND p.subscription_expires_at > NOW()
          ), FALSE) DESC,
          (
              6371 * acos(
                  GREATEST(-1.0, LEAST(1.0,
                      cos(radians(:lat)) * cos(radians(p.geo_lat))
                      * cos(radians(p.geo_lng) - radians(:lng))
                      + sin(radians(:lat)) * sin(radians(p.geo_lat))
                  ))
              )
          ) ASC
        LIMIT :maxSize
        """, nativeQuery = true)
    List<UUID> findNearbyProfessionalIdsExcluding(
            @Param("categoryId") UUID categoryId,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm,
            @Param("maxSize") int maxSize,
            @Param("excludeIds") List<UUID> excludeIds
    );
}
