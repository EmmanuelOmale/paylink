package com.paylink.transfer.api;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends ApiException {

    public IdempotencyKeyReusedException(String rrn) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED",
                "Idempotency-Key was already used with a different request", rrn);
    }
}
