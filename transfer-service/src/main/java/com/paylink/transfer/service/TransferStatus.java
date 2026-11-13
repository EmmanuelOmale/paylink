package com.paylink.transfer.service;

public enum TransferStatus {
    INITIATED,
    RISK_APPROVED,
    COMPLETED,
    FAILED;

    public boolean canMoveTo(TransferStatus next) {
        return switch (this) {
            case INITIATED -> next == RISK_APPROVED || next == FAILED;
            case RISK_APPROVED -> next == COMPLETED || next == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }
}
