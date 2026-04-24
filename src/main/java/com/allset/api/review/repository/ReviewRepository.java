package com.allset.api.review.repository;

import com.allset.api.order.domain.Order;
import com.allset.api.review.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    interface RatingSummaryView {
        Double getAverageRating();
        long getReviewCount();
    }

    Optional<Review> findByOrderIdAndReviewerId(UUID orderId, UUID reviewerId);

    List<Review> findAllByOrderIdOrderBySubmittedAtAsc(UUID orderId);

    Page<Review> findAllByRevieweeIdAndPublishedAtIsNotNull(UUID revieweeId, Pageable pageable);

    @Query("""
        select r
          from Review r, Order o
         where r.orderId = o.id
           and r.revieweeId = :revieweeId
           and o.serviceId = :serviceId
           and r.publishedAt is not null
        """)
    Page<Review> findPublishedServiceReviews(
            @Param("revieweeId") UUID revieweeId,
            @Param("serviceId") UUID serviceId,
            Pageable pageable
    );

    @Query("""
        select avg(r.rating) as averageRating, count(r) as reviewCount
          from Review r
         where r.revieweeId = :revieweeId
           and r.publishedAt is not null
        """)
    RatingSummaryView summarizePublishedByRevieweeId(@Param("revieweeId") UUID revieweeId);

    @Query("""
        select avg(r.rating) as averageRating, count(r) as reviewCount
          from Review r, Order o
         where r.orderId = o.id
           and r.revieweeId = :revieweeId
           and o.serviceId = :serviceId
           and r.publishedAt is not null
        """)
    RatingSummaryView summarizePublishedServiceReviews(
            @Param("revieweeId") UUID revieweeId,
            @Param("serviceId") UUID serviceId
    );

    @Query("""
        select r
          from Review r, Order o
         where r.orderId = o.id
           and r.publishedAt is null
           and o.completedAt is not null
           and o.completedAt <= :completedBefore
        """)
    List<Review> findAllReadyToPublish(@Param("completedBefore") Instant completedBefore);

    @Modifying
    @Query("""
        update Review r
           set r.publishedAt = :publishedAt
         where r.orderId = :orderId
           and r.publishedAt is null
        """)
    int publishOrderReviews(@Param("orderId") UUID orderId, @Param("publishedAt") Instant publishedAt);
}
