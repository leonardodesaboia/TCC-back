package com.allset.api.review.service;

import com.allset.api.review.dto.CreateReviewRequest;
import com.allset.api.review.dto.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ReviewService {

    ReviewResponse create(UUID orderId, UUID reviewerUserId, String reviewerRole, CreateReviewRequest request);

    List<ReviewResponse> listOrderReviews(UUID orderId, UUID requesterUserId, String requesterRole);

    Page<ReviewResponse> listProfessionalReviews(UUID professionalId, Pageable pageable);

    Page<ReviewResponse> listServiceReviews(UUID professionalId, UUID serviceId, Pageable pageable);
}
