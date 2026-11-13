package com.paylink.transfer.api;

import java.math.BigDecimal;

public record TransferRequest(
        String sourceWalletId,
        String destinationWalletId,
        BigDecimal amount,
        String currency,
        String remarks) {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    public void validate() {
        checkWalletId(sourceWalletId, "sourceWalletId");
        checkWalletId(destinationWalletId, "destinationWalletId");
        if (sourceWalletId.equals(destinationWalletId)) {
            throw new InvalidRequestException("Cannot transfer to the same wallet");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidRequestException("amount must be greater than 0");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidRequestException("amount can have at most 2 decimal places");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidRequestException("amount cannot be more than 1000000.00");
        }
        if (currency == null || !currency.matches("[A-Za-z]{3}")) {
            throw new InvalidRequestException("currency must be a 3-letter code");
        }
        if (remarks != null && remarks.trim().length() > 140) {
            throw new InvalidRequestException("remarks cannot be longer than 140 characters");
        }
    }

    private static void checkWalletId(String id, String field) {
        if (id == null || id.isBlank() || id.length() > 20) {
            throw new InvalidRequestException(field + " is required (max 20 characters)");
        }
    }
}
