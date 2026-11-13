package com.paylink.transfer.risk;

import java.math.BigDecimal;

public interface RiskScreeningClient {
    RiskDecision screen(String rrn, String sourceWalletId, String destinationWalletId, BigDecimal amount);
}
