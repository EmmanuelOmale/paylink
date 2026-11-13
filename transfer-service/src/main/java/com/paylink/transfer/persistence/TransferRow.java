package com.paylink.transfer.persistence;

import java.math.BigDecimal;
import java.time.Instant;

import com.paylink.transfer.service.FailureReason;
import com.paylink.transfer.service.TransferStatus;

public record TransferRow(
        String rrn,
        String sourceWalletId,
        String destinationWalletId,
        BigDecimal amount,
        String currency,
        String remarks,
        TransferStatus status,
        FailureReason failureReason,
        Instant createdAt) {

    public TransferRow withStatus(TransferStatus next, FailureReason reason) {
        return new TransferRow(rrn, sourceWalletId, destinationWalletId, amount, currency, remarks, next, reason, createdAt);
    }
}
