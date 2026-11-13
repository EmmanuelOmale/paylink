package com.paylink.transfer.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ApiError> rateLimited(RateLimitedException e) {
        return ResponseEntity.status(e.status())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()))
                .body(e.toError());
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> rejected(ApiException e) {
        return ResponseEntity.status(e.status()).body(e.toError());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.internalServerError()
                .body(new ApiError("INTERNAL_ERROR", "An unexpected error occurred", null));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        return new ResponseEntity<>(new ApiError(codeFor(status), messageFor(e, status), null), headers, status);
    }

    private static String codeFor(HttpStatus status) {
        if (status == HttpStatus.BAD_REQUEST) {
            return "INVALID_REQUEST";
        }
        return status.is5xxServerError() ? "INTERNAL_ERROR" : status.name();
    }

    private static String messageFor(Exception e, HttpStatus status) {
        if (e instanceof HttpMessageNotReadableException) {
            return "Invalid or missing JSON body";
        }
        return status.getReasonPhrase();
    }
}
