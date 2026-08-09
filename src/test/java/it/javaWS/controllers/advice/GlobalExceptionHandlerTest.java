package it.javaWS.controllers.advice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import it.javaWS.utils.InvalidBillException;
import it.javaWS.utils.UnauthorizedAccessException;
import jakarta.persistence.EntityNotFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returnsNotFound() {
        ResponseEntity<?> response = handler.handleNotFound(new EntityNotFoundException("Not found"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleBadRequest_returnsBadRequest() {
        ResponseEntity<?> response = handler.handleBadRequest(new IllegalArgumentException("Bad request"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleAccessDenied_returnsForbidden() {
        ResponseEntity<?> response = handler.handleAccessDenied(new AccessDeniedException("Denied"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleAuthentication_returnsUnauthorized() {
        ResponseEntity<?> response = handler.handleAuthentication(new AuthenticationException("Auth failed") {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handleUnauthorizedAccess_returnsUnauthorized() {
        ResponseEntity<?> response = handler.handleUnauthorizedAccess(new UnauthorizedAccessException("Unauthorized"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handleInvalidBill_returnsBadRequest() {
        ResponseEntity<?> response = handler.handleInvalidBill(new InvalidBillException("Invalid"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleGeneric_returnsInternalServerError() {
        ResponseEntity<?> response = handler.handleGeneric(new RuntimeException("Unexpected"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
