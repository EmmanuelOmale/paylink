package com.paylink.transfer.api;

import org.springframework.http.HttpStatus;

public class TransferNotFoundException extends ApiException {

    public TransferNotFoundException(String rrn) {
        super(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", "Transfer not found: " + rrn, null);
    }
}
