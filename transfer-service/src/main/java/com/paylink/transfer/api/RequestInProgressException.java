package com.paylink.transfer.api;

import org.springframework.http.HttpStatus;

public class RequestInProgressException extends ApiException {

    public RequestInProgressException(String rrn) {
        super(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", "A request with this Idempotency-Key is still in progress", rrn);
    }
}
