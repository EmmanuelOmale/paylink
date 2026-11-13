package com.paylink.transfer.api;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String rrn;

    protected ApiException(HttpStatus status, String code, String message, String rrn) {
        super(message);
        this.status = status;
        this.code = code;
        this.rrn = rrn;
    }

    public HttpStatus status() {
        return status;
    }

    public ApiError toError() {
        return new ApiError(code, getMessage(), rrn);
    }
}
