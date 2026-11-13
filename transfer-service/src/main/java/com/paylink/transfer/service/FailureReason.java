package com.paylink.transfer.service;

public enum FailureReason {
    INSUFFICIENT_FUNDS(422, "Insufficient funds"),
    RISK_DECLINED(422, "Declined by risk screening"),
    RISK_UNAVAILABLE(503, "Risk screening is unavailable");

    private final int httpStatus;
    private final String message;

    FailureReason(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}
