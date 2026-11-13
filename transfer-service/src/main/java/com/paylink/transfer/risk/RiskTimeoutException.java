package com.paylink.transfer.risk;

public class RiskTimeoutException extends RuntimeException {

    public RiskTimeoutException() {
        super("Risk screening timed out");
    }
}
