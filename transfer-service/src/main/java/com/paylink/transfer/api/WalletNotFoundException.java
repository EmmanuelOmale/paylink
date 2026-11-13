package com.paylink.transfer.api;

import org.springframework.http.HttpStatus;

public class WalletNotFoundException extends ApiException {

    public WalletNotFoundException(String walletId) {
        super(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found: " + walletId, null);
    }
}
