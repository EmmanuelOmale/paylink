package com.paylink.transfer.risk;

public class RiskClientException extends RuntimeException {

    public RiskClientException(int statusCode) {
        super("Risk screening rejected the call with HTTP " + statusCode);
    }
}
