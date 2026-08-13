package it.javaWS.controllers.advice;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import it.javaWS.utils.BillNotFoundException;
import it.javaWS.utils.DuplicateUserException;
import it.javaWS.utils.FriendshipNotFoundException;
import it.javaWS.utils.GroupNotFoundException;
import it.javaWS.utils.InvalidBillException;
import it.javaWS.utils.InvalidCredentialsException;
import it.javaWS.utils.InvalidPaymentException;
import it.javaWS.utils.NotGroupAdminException;
import it.javaWS.utils.PaymentExceedsDebtException;
import it.javaWS.utils.PendingSettlementsException;
import it.javaWS.utils.TransactionNotFoundException;
import it.javaWS.utils.UnauthorizedAccessException;
import it.javaWS.utils.UserNotFoundException;
import jakarta.persistence.EntityNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(InvalidBillException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidBill(InvalidBillException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateUser(DuplicateUserException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleGroupNotFound(GroupNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NotGroupAdminException.class)
    public ResponseEntity<Map<String, Object>> handleNotGroupAdmin(NotGroupAdminException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(PendingSettlementsException.class)
    public ResponseEntity<Map<String, Object>> handlePendingSettlements(PendingSettlementsException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({UserNotFoundException.class, BillNotFoundException.class, TransactionNotFoundException.class, FriendshipNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleDomainNotFound(RuntimeException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidPaymentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPayment(InvalidPaymentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PaymentExceedsDebtException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentExceedsDebt(PaymentExceedsDebtException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Errore interno del server");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
    }
}
