package com.allset.api.shared.exception;

import com.allset.api.catalog.exception.ServiceAreaNameAlreadyExistsException;
import com.allset.api.catalog.exception.ServiceAreaNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnValidationPayload() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "Nome é obrigatório"));

        Method method = DummyController.class.getDeclaredMethod("handle", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiError> response = handler.handleValidation(exception, request("POST", "/api/v1/service-areas"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().fields()).containsEntry("name", "Nome é obrigatório");
    }

    @Test
    void shouldReturnNotFoundPayload() {
        UUID id = UUID.randomUUID();

        ResponseEntity<ApiError> response = handler.handleNotFound(
                new ServiceAreaNotFoundException(id),
                request("GET", "/api/v1/service-areas/" + id)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).contains(id.toString());
    }

    @Test
    void shouldReturnConflictPayload() {
        ResponseEntity<ApiError> response = handler.handleConflict(
                new ServiceAreaNameAlreadyExistsException("Eletrica"),
                request("POST", "/api/v1/service-areas")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("Eletrica");
    }

    @Test
    void shouldReturnBadRequestForIllegalArgument() {
        ResponseEntity<ApiError> response = handler.handleIllegalArgument(
                new IllegalArgumentException("weekday é obrigatório"),
                request("POST", "/api/v1/professionals/1/calendar/blocks")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).contains("weekday");
    }

    private HttpServletRequest request(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    static class DummyController {
        @SuppressWarnings("unused")
        void handle(String body) {
        }
    }
}
