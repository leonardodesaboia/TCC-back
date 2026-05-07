package com.allset.api.review.service;

import com.allset.api.offering.domain.ProfessionalOffering;
import com.allset.api.offering.repository.ProfessionalOfferingRepository;
import com.allset.api.order.domain.Order;
import com.allset.api.order.domain.OrderMode;
import com.allset.api.order.domain.OrderStatus;
import com.allset.api.order.exception.OrderNotFoundException;
import com.allset.api.order.repository.OrderRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.review.domain.Review;
import com.allset.api.review.dto.CreateReviewRequest;
import com.allset.api.review.dto.ReviewResponse;
import com.allset.api.review.exception.ReviewAlreadyExistsException;
import com.allset.api.review.mapper.ReviewMapper;
import com.allset.api.review.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private ProfessionalOfferingRepository professionalOfferingRepository;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private ReviewPublicationService reviewPublicationService;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Test
    void createShouldRejectDuplicateReviewForSameOrderAndReviewer() {
        UUID orderId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(buildCompletedOrder(orderId)));
        when(reviewRepository.findByOrderIdAndReviewerId(orderId, reviewerId))
                .thenReturn(Optional.of(Review.builder().orderId(orderId).reviewerId(reviewerId).build()));

        assertThatThrownBy(() -> reviewService.create(orderId, reviewerId, "client", new CreateReviewRequest((short) 5, "Excelente")))
                .isInstanceOf(ReviewAlreadyExistsException.class);
    }

    @Test
    void createByClientShouldRequireComment() {
        UUID orderId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Order order = buildCompletedOrder(orderId);
        order.setClientId(reviewerId);

        Professional professional = Professional.builder().userId(UUID.randomUUID()).build();
        professional.setId(order.getProfessionalId());

        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(reviewRepository.findByOrderIdAndReviewerId(orderId, reviewerId)).thenReturn(Optional.empty());
        when(professionalRepository.findByIdAndDeletedAtIsNull(order.getProfessionalId())).thenReturn(Optional.of(professional));

        assertThatThrownBy(() -> reviewService.create(orderId, reviewerId, "client", new CreateReviewRequest((short) 5, " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Comentario");
    }

    @Test
    void createByProfessionalShouldRejectComment() {
        UUID orderId = UUID.randomUUID();
        UUID professionalUserId = UUID.randomUUID();
        Order order = buildCompletedOrder(orderId);

        Professional professional = Professional.builder().userId(professionalUserId).build();
        professional.setId(order.getProfessionalId());

        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(reviewRepository.findByOrderIdAndReviewerId(orderId, professionalUserId)).thenReturn(Optional.empty());
        when(professionalRepository.findByUserIdAndDeletedAtIsNull(professionalUserId)).thenReturn(Optional.of(professional));

        assertThatThrownBy(() -> reviewService.create(orderId, professionalUserId, "professional", new CreateReviewRequest((short) 4, "Nao pode")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao pode enviar comentario");
    }

    @Test
    void createByClientShouldPersistReviewAndTriggerPublicationCheck() {
        UUID orderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID professionalUserId = UUID.randomUUID();
        Order order = buildCompletedOrder(orderId);
        order.setClientId(clientId);

        Professional professional = Professional.builder().userId(professionalUserId).build();
        professional.setId(order.getProfessionalId());

        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(reviewRepository.findByOrderIdAndReviewerId(orderId, clientId)).thenReturn(Optional.empty());
        when(professionalRepository.findByIdAndDeletedAtIsNull(order.getProfessionalId())).thenReturn(Optional.of(professional));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            review.setId(UUID.randomUUID());
            review.setSubmittedAt(Instant.now());
            return review;
        });
        when(reviewMapper.toResponse(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            return new ReviewResponse(
                    review.getId(),
                    review.getOrderId(),
                    review.getReviewerId(),
                    review.getRevieweeId(),
                    review.getRating(),
                    review.getComment(),
                    review.getSubmittedAt(),
                    review.getPublishedAt()
            );
        });
        when(reviewPublicationService.publishOrderIfReady(orderId)).thenReturn(null);

        ReviewResponse response = reviewService.create(orderId, clientId, "client", new CreateReviewRequest((short) 5, "Excelente"));

        assertThat(response.revieweeId()).isEqualTo(professionalUserId);
        assertThat(response.comment()).isEqualTo("Excelente");
        verify(reviewPublicationService).publishOrderIfReady(orderId);
    }

    @Test
    void listOrderReviewsShouldHideOtherUsersUnpublishedReview() {
        UUID orderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID professionalUserId = UUID.randomUUID();
        Order order = buildCompletedOrder(orderId);
        order.setClientId(clientId);

        Review ownReview = Review.builder()
                .orderId(orderId)
                .reviewerId(clientId)
                .revieweeId(professionalUserId)
                .rating((short) 5)
                .comment("Excelente")
                .submittedAt(Instant.now())
                .build();

        Review hiddenReview = Review.builder()
                .orderId(orderId)
                .reviewerId(professionalUserId)
                .revieweeId(clientId)
                .rating((short) 4)
                .submittedAt(Instant.now())
                .build();

        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        when(reviewRepository.findAllByOrderIdOrderBySubmittedAtAsc(orderId)).thenReturn(List.of(ownReview, hiddenReview));
        when(reviewMapper.toResponse(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            return new ReviewResponse(
                    UUID.randomUUID(),
                    review.getOrderId(),
                    review.getReviewerId(),
                    review.getRevieweeId(),
                    review.getRating(),
                    review.getComment(),
                    review.getSubmittedAt(),
                    review.getPublishedAt()
            );
        });

        List<ReviewResponse> responses = reviewService.listOrderReviews(orderId, clientId, "client");

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().reviewerId()).isEqualTo(clientId);
        verify(reviewPublicationService).publishExpiredReviews();
        verify(professionalRepository, never()).findByUserIdAndDeletedAtIsNull(clientId);
    }

    @Test
    void listServiceReviewsShouldRequireOfferingOwnership() {
        UUID professionalId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID professionalUserId = UUID.randomUUID();

        Professional professional = Professional.builder().userId(professionalUserId).build();
        professional.setId(professionalId);

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));
        when(professionalOfferingRepository.findByIdAndProfessionalIdAndDeletedAtIsNull(serviceId, professionalId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.listServiceReviews(professionalId, serviceId, PageRequest.of(0, 20)))
                .isInstanceOf(com.allset.api.offering.exception.ProfessionalOfferingNotFoundException.class);
    }

    @Test
    void listProfessionalReviewsShouldReturnPublishedItems() {
        UUID professionalId = UUID.randomUUID();
        UUID professionalUserId = UUID.randomUUID();

        Professional professional = Professional.builder().userId(professionalUserId).build();
        professional.setId(professionalId);

        Review publishedReview = Review.builder()
                .orderId(UUID.randomUUID())
                .reviewerId(UUID.randomUUID())
                .revieweeId(professionalUserId)
                .rating((short) 5)
                .comment("Excelente")
                .submittedAt(Instant.now())
                .publishedAt(Instant.now())
                .build();

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));
        when(reviewRepository.findAllByRevieweeIdAndPublishedAtIsNotNull(professionalUserId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(publishedReview)));
        when(reviewMapper.toResponse(publishedReview)).thenReturn(new ReviewResponse(
                UUID.randomUUID(),
                publishedReview.getOrderId(),
                publishedReview.getReviewerId(),
                publishedReview.getRevieweeId(),
                publishedReview.getRating(),
                publishedReview.getComment(),
                publishedReview.getSubmittedAt(),
                publishedReview.getPublishedAt()
        ));

        assertThat(reviewService.listProfessionalReviews(professionalId, PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    private Order buildCompletedOrder(UUID orderId) {
        Order order = Order.builder()
                .clientId(UUID.randomUUID())
                .professionalId(UUID.randomUUID())
                .serviceId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .mode(OrderMode.on_demand)
                .status(OrderStatus.completed)
                .description("Servico concluido")
                .addressId(UUID.randomUUID())
                .expiresAt(Instant.now())
                .completedAt(Instant.now())
                .build();
        order.setId(orderId);
        return order;
    }
}
