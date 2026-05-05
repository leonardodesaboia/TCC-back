package com.allset.api.shared.exception;

import com.allset.api.auth.exception.InvalidCredentialsException;
import com.allset.api.chat.exception.ConversationNotFoundException;
import com.allset.api.chat.exception.MessageContentInvalidException;
import com.allset.api.auth.exception.InvalidResetCodeException;
import com.allset.api.auth.exception.InvalidTokenException;
import com.allset.api.address.exception.SavedAddressNotFoundException;
import com.allset.api.notification.exception.NotificationNotFoundException;
import com.allset.api.notification.exception.PushTokenNotFoundException;
import com.allset.api.order.exception.ExpressQueueViolationException;
import com.allset.api.order.exception.NoProfessionalsAvailableException;
import com.allset.api.order.exception.OrderNotFoundException;
import com.allset.api.order.exception.OrderStatusTransitionException;
import com.allset.api.order.exception.ProposalWindowExpiredException;
import com.allset.api.calendar.exception.BlockedPeriodNotFoundException;
import com.allset.api.catalog.exception.ServiceAreaNameAlreadyExistsException;
import com.allset.api.catalog.exception.ServiceAreaNotFoundException;
import com.allset.api.catalog.exception.ServiceCategoryNotFoundException;
import com.allset.api.dispute.exception.DisputeAlreadyExistsException;
import com.allset.api.dispute.exception.DisputeNotFoundException;
import com.allset.api.dispute.exception.DisputeStatusTransitionException;
import com.allset.api.dispute.exception.DisputeWindowExpiredException;
import com.allset.api.document.exception.ProfessionalDocumentNotFoundException;
import com.allset.api.favorite.exception.FavoriteProfessionalAlreadyExistsException;
import com.allset.api.favorite.exception.FavoriteProfessionalNotFoundException;
import com.allset.api.offering.exception.ProfessionalOfferingNotFoundException;
import com.allset.api.professional.exception.ProfessionalAlreadyExistsException;
import com.allset.api.professional.exception.ProfessionalNotApprovedException;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.review.exception.ReviewAlreadyExistsException;
import com.allset.api.integration.storage.exception.FileTooLargeException;
import com.allset.api.integration.storage.exception.InvalidFileTypeException;
import com.allset.api.integration.storage.exception.StorageObjectNotFoundException;
import com.allset.api.integration.storage.exception.StorageUploadException;
import com.allset.api.subscription.exception.SubscriptionPlanNameAlreadyExistsException;
import com.allset.api.subscription.exception.SubscriptionPlanNotFoundException;
import com.allset.api.subscription.exception.ProfessionalSubscriptionNotFoundException;
import com.allset.api.subscription.exception.SubscriptionPlanAlreadyActiveException;
import com.allset.api.user.exception.CpfAlreadyExistsException;
import com.allset.api.user.exception.EmailAlreadyExistsException;
import com.allset.api.user.exception.UserBannedException;
import com.allset.api.user.exception.UserNotFoundException;
import com.allset.api.user.exception.UserPendingDeletionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));

        log.warn("status=400 method={} path={} fields={}",
            request.getMethod(), request.getRequestURI(), fields);

        return ResponseEntity.badRequest().body(new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            "Validation failed",
            fields,
            Instant.now()
        ));
    }

    @ExceptionHandler({
            UserNotFoundException.class,
            SavedAddressNotFoundException.class,
            OrderNotFoundException.class,
            ProfessionalNotFoundException.class,
            ServiceAreaNotFoundException.class,
            ServiceCategoryNotFoundException.class,
            SubscriptionPlanNotFoundException.class,
            ProfessionalSubscriptionNotFoundException.class,
            ProfessionalDocumentNotFoundException.class,
            ProfessionalOfferingNotFoundException.class,
            BlockedPeriodNotFoundException.class,
            ConversationNotFoundException.class,
            NotificationNotFoundException.class,
            PushTokenNotFoundException.class,
            StorageObjectNotFoundException.class,
            DisputeNotFoundException.class,
            FavoriteProfessionalNotFoundException.class
    })
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        log.warn("status=404 method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            null,
            Instant.now()
        ));
    }

    @ExceptionHandler({
            EmailAlreadyExistsException.class,
            CpfAlreadyExistsException.class,
            ProfessionalAlreadyExistsException.class,
            ServiceAreaNameAlreadyExistsException.class,
            SubscriptionPlanNameAlreadyExistsException.class,
            SubscriptionPlanAlreadyActiveException.class,
            ReviewAlreadyExistsException.class,
            DisputeAlreadyExistsException.class,
            FavoriteProfessionalAlreadyExistsException.class
    })
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex,
                                                    HttpServletRequest request) {
        log.warn("status=409 method={} path={} exception={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
            HttpStatus.CONFLICT.value(),
            ex.getMessage(),
            null,
            Instant.now()
        ));
    }

    @ExceptionHandler(UserPendingDeletionException.class)
    public ResponseEntity<ApiError> handlePendingDeletion(UserPendingDeletionException ex,
                                                          HttpServletRequest request) {
        log.warn("status=423 method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.LOCKED).body(new ApiError(
            HttpStatus.LOCKED.value(),
            ex.getMessage(),
            Map.of("scheduledDeletionAt", ex.getScheduledDeletionAt().toString()),
            Instant.now()
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("status=403 method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
            HttpStatus.FORBIDDEN.value(),
            "Acesso negado",
            null,
            Instant.now()
        ));
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidTokenException.class})
    public ResponseEntity<ApiError> handleUnauthorized(RuntimeException ex, HttpServletRequest request) {
        log.warn("status=401 method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(
            HttpStatus.UNAUTHORIZED.value(),
            ex.getMessage(),
            null,
            Instant.now()
        ));
    }

    @ExceptionHandler(InvalidResetCodeException.class)
    public ResponseEntity<ApiError> handleInvalidResetCode(InvalidResetCodeException ex,
                                                           HttpServletRequest request) {
        log.warn("status=400 method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest().body(new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            ex.getMessage(),
            null,
            Instant.now()
        ));
    }

    @ExceptionHandler(ProfessionalNotApprovedException.class)
    public ResponseEntity<ApiError> handleNotApproved(ProfessionalNotApprovedException ex,
                                                      HttpServletRequest request) {
        log.warn("status=403 method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
            HttpStatus.FORBIDDEN.value(),
            ex.getMessage(),
            null,
            Instant.now()
        ));
    }

    @ExceptionHandler(UserBannedException.class)
    public ResponseEntity<ApiError> handleBanned(UserBannedException ex,
                                                 HttpServletRequest request) {
        log.warn("status=403 method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
            HttpStatus.FORBIDDEN.value(),
            ex.getMessage(),
            null,
            Instant.now()
        ));
    }

    @ExceptionHandler(MessageContentInvalidException.class)
    public ResponseEntity<ApiError> handleMessageContentInvalid(MessageContentInvalidException ex,
                                                                 HttpServletRequest request) {
        log.warn("status=400 method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest().body(new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            ex.getMessage(),
            null,
            Instant.now()
        ));
    }

    @ExceptionHandler({
            OrderStatusTransitionException.class,
            ExpressQueueViolationException.class,
            DisputeStatusTransitionException.class,
            DisputeWindowExpiredException.class
    })
    public ResponseEntity<ApiError> handleOrderBusiness(RuntimeException ex, HttpServletRequest request) {
        log.warn("status=400 method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest().body(new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            ex.getMessage(),
            null,
            Instant.now()
        ));
    }

    @ExceptionHandler(ProposalWindowExpiredException.class)
    public ResponseEntity<ApiError> handleProposalWindowExpired(ProposalWindowExpiredException ex,
                                                                HttpServletRequest request) {
        log.warn("status=409 method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
            HttpStatus.CONFLICT.value(),
            ex.getMessage(),
            null,
            Instant.now()
        ));
    }

    @ExceptionHandler(NoProfessionalsAvailableException.class)
    public ResponseEntity<ApiError> handleNoProfessionals(NoProfessionalsAvailableException ex,
                                                          HttpServletRequest request) {
        log.warn("status=422 method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.unprocessableEntity().body(new ApiError(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            ex.getMessage(),
            null,
            Instant.now()
        ));
    }

    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ApiError> handleInvalidFileType(InvalidFileTypeException ex, HttpServletRequest request) {
        log.warn("status=400 method={} path={} message={}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest().body(new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                null,
                Instant.now()
        ));
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<ApiError> handleFileTooLarge(FileTooLargeException ex, HttpServletRequest request) {
        log.warn("status=413 method={} path={} message={}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiError(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                ex.getMessage(),
                null,
                Instant.now()
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("status=413 method={} path={} message={}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiError(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "Arquivo excede o limite máximo permitido pelo servidor",
                null,
                Instant.now()
        ));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleMultipart(MultipartException ex, HttpServletRequest request) {
        log.warn("status=400 method={} path={} message={}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest().body(new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                "Requisição multipart inválida",
                null,
                Instant.now()
        ));
    }

    @ExceptionHandler(StorageUploadException.class)
    public ResponseEntity<ApiError> handleStorageUpload(StorageUploadException ex, HttpServletRequest request) {
        log.error("event=storage_upload_failed status=500 method={} path={} message={}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Falha ao processar arquivo no storage",
                null,
                Instant.now()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("status=400 method={} path={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest().body(new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            ex.getMessage(),
            null,
            Instant.now()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("status=500 method={} path={} exception={} message={}",
            request.getMethod(), request.getRequestURI(), ex.getClass().getName(), ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal server error",
            null,
            Instant.now()
        ));
    }
}
