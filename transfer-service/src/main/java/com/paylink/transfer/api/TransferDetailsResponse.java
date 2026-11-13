package com.paylink.transfer.api;

import java.time.temporal.ChronoUnit;

import com.paylink.transfer.persistence.TransferRow;

public record TransferDetailsResponse(
        String rrn,
        String status,
        String sourceWalletId,
        String destinationWalletId,
        String amount,
        String currency,
        String createdAt,
        String failureReason) {

    public static TransferDetailsResponse from(TransferRow transfer) {
        return new TransferDetailsResponse(
                transfer.rrn(),
                transfer.status().name(),
                transfer.sourceWalletId(),
                transfer.destinationWalletId(),
                transfer.amount().setScale(2).toPlainString(),
                transfer.currency(),
                transfer.createdAt().truncatedTo(ChronoUnit.SECONDS).toString(),
                transfer.failureReason() == null ? null : transfer.failureReason().name());
    }
}
