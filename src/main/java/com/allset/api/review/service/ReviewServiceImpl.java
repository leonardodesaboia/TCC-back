package com.allset.api.review.service;

import com.allset.api.offering.exception.ProfessionalOfferingNotFoundException;
import com.allset.api.offering.repository.ProfessionalOfferingRepository;
import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.exception.OrderNotFoundException;
import com.allset.api.order.repository.OrderRepository;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.review.domain.Review;
import com.allset.api.review.dto.CreateReviewRequest;
import com.allset.api.review.dto.ReviewResponse;
import com.allset.api.review.exception.ReviewAlreadyExistsException;
import com.allset.api.review.mapper.ReviewMapper;
import com.allset.api.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProfessionalRepository professionalRepository;
    private final ProfessionalOfferingRepository professionalOfferingRepository;
    private final ReviewMapper reviewMapper;
    private final ReviewPublicationService reviewPublicationService;

    @Override
    public ReviewResponse create(UUID orderId, UUID reviewerUserId, String reviewerRole, CreateReviewRequest request) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.completed || order.getCompletedAt() == null) {
            throw new IllegalArgumentException("Avaliacoes so podem ser enviadas para pedidos concluidos");
        }

        if (reviewRepository.findByOrderIdAndReviewerId(orderId, reviewerUserId).isPresent()) {
            throw new ReviewAlreadyExistsException(orderId, reviewerUserId);
        }

        Review review = buildReview(order, reviewerUserId, reviewerRole, request);
        Review saved = reviewRepository.save(review);

        Instant publishedAt = reviewPublicationService.publishOrderIfReady(orderId);
        if (publishedAt != null) {
            saved.setPublishedAt(publishedAt);
        }

        return reviewMapper.toResponse(saved);
    }

    @Override
    public List<ReviewResponse> listOrderReviews(UUID orderId, UUID requesterUserId, String requesterRole) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        boolean isAdmin = "admin".equals(requesterRole);
        boolean isClient = requesterUserId.equals(order.getClientId());
        boolean isProfessional = false;

        if ("professional".equals(requesterRole)) {
            isProfessional = professionalRepository.findByUserIdAndDeletedAtIsNull(requesterUserId)
                    .map(professional -> professional.getId().equals(order.getProfessionalId()))
                    .orElse(false);
        }

        if (!isAdmin && !isClient && !isProfessional) {
            throw new OrderNotFoundException(orderId);
        }

        return reviewRepository.findAllByOrderIdOrderBySubmittedAtAsc(orderId).stream()
                .filter(review -> isAdmin
                        || review.getPublishedAt() != null
                        || review.getReviewerId().equals(requesterUserId))
                .map(reviewMapper::toResponse)
                .toList();
    }

    @Override
    public Page<ReviewResponse> listProfessionalReviews(UUID professionalId, Pageable pageable) {
        UUID professionalUserId = professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .map(professional -> professional.getUserId())
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));

        return reviewRepository.findAllByRevieweeIdAndPublishedAtIsNotNull(professionalUserId, pageable)
                .map(reviewMapper::toResponse);
    }

    @Override
    public Page<ReviewResponse> listServiceReviews(UUID professionalId, UUID serviceId, Pageable pageable) {
        UUID professionalUserId = professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .map(professional -> professional.getUserId())
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));

        professionalOfferingRepository.findByIdAndProfessionalIdAndDeletedAtIsNull(serviceId, professionalId)
                .orElseThrow(() -> new ProfessionalOfferingNotFoundException(serviceId));

        return reviewRepository.findPublishedServiceReviews(professionalUserId, serviceId, pageable)
                .map(reviewMapper::toResponse);
    }

    private Review buildReview(Order order, UUID reviewerUserId, String reviewerRole, CreateReviewRequest request) {
        if ("client".equals(reviewerRole)) {
            if (!reviewerUserId.equals(order.getClientId())) {
                throw new OrderNotFoundException(order.getId());
            }

            UUID professionalUserId = professionalRepository.findByIdAndDeletedAtIsNull(order.getProfessionalId())
                    .map(professional -> professional.getUserId())
                    .orElseThrow(() -> new ProfessionalNotFoundException(order.getProfessionalId()));

            if (request.comment() == null || request.comment().isBlank()) {
                throw new IllegalArgumentException("Comentario e obrigatorio para a avaliacao do cliente");
            }

            return Review.builder()
                    .orderId(order.getId())
                    .reviewerId(reviewerUserId)
                    .revieweeId(professionalUserId)
                    .rating(request.rating())
                    .comment(request.comment().trim())
                    .build();
        }

        if ("professional".equals(reviewerRole)) {
            UUID professionalId = professionalRepository.findByUserIdAndDeletedAtIsNull(reviewerUserId)
                    .map(professional -> professional.getId())
                    .orElseThrow(() -> new ProfessionalNotFoundException(reviewerUserId));

            if (!professionalId.equals(order.getProfessionalId())) {
                throw new OrderNotFoundException(order.getId());
            }

            if (request.comment() != null && !request.comment().isBlank()) {
                throw new IllegalArgumentException("Profissional nao pode enviar comentario na avaliacao do cliente");
            }

            return Review.builder()
                    .orderId(order.getId())
                    .reviewerId(reviewerUserId)
                    .revieweeId(order.getClientId())
                    .rating(request.rating())
                    .comment(null)
                    .build();
        }

        throw new IllegalArgumentException("Apenas cliente ou profissional podem enviar avaliacoes");
    }
}
