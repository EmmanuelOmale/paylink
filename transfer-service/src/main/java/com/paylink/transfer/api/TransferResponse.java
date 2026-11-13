package com.paylink.transfer.api;

import java.time.temporal.ChronoUnit;

import com.paylink.transfer.persistence.TransferRow;

public record TransferResponse(
        String rrn,
        String status,
        String sourceWalletId,
        String destinationWalletId,
        String amount,
        String currency,
        String createdAt) {

    public static TransferResponse from(TransferRow transfer) {
        return new TransferResponse(
                transfer.rrn(),
                transfer.status().name(),
                transfer.sourceWalletId(),
                transfer.destinationWalletId(),
                transfer.amount().setScale(2).toPlainString(),
                transfer.currency(),
                transfer.createdAt().truncatedTo(ChronoUnit.SECONDS).toString());
    }
}
