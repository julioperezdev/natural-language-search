package dev.julioperez.nls.products.infrastructure.http;

import dev.julioperez.nls.products.domain.search.ProductSearchException;
import dev.julioperez.nls.products.application.SearchInterpretationUnavailableException;
import dev.julioperez.nls.products.application.SearchInterpretationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProductSearchExceptionHandler {
    @ExceptionHandler(ProductSearchException.class)
    public ResponseEntity<SearchErrorResponse> searchError(
            ProductSearchException exception,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(SearchInterpretationUnavailableException.class)
    public ResponseEntity<SearchErrorResponse> interpretationUnavailable(HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_INTERPRETATION_UNAVAILABLE",
                "Natural-language interpretation is temporarily unavailable.", request);
    }

    @ExceptionHandler(SearchInterpretationFailedException.class)
    public ResponseEntity<SearchErrorResponse> interpretationFailed(HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "SEARCH_INTERPRETATION_FAILED",
                "Search intent could not be interpreted safely.", request);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<SearchErrorResponse> invalidRequest(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_REQUEST",
                "Search request is invalid.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<SearchErrorResponse> internalError(HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The request could not be completed.", request);
    }

    private ResponseEntity<SearchErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(
                new SearchErrorResponse(code, message, Instant.now(), request.getRequestId()));
    }
}
