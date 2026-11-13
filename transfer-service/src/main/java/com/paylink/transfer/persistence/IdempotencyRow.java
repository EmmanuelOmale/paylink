package com.paylink.transfer.persistence;

import java.time.Instant;

public record IdempotencyRow(
        String requestHash,
        String status,
        String rrn,
        Integer httpStatus,
        String responseBody,
        Instant createdAt) {

    public boolean isInProgress() {
        return "IN_PROGRESS".equals(status);
    }
}
