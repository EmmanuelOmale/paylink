package com.paylink.transfer.risk;

public record RiskDecision(Outcome outcome, String reasonCode) {
    public enum Outcome { APPROVED, DECLINED }
}
