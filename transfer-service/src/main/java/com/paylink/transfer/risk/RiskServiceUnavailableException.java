package com.paylink.transfer.risk;

public class RiskServiceUnavailableException extends RuntimeException {

    public RiskServiceUnavailableException(int statusCode) {
        super("Risk screening returned HTTP " + statusCode);
    }
}
