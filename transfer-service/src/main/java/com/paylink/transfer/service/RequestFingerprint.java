package com.paylink.transfer.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import com.paylink.transfer.api.TransferRequest;

public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    public static String of(TransferRequest request) {
        String canonical = String.join("|",
                request.destinationWalletId(),
                request.amount().setScale(2).toPlainString(),
                request.currency().toUpperCase(Locale.ROOT),
                request.remarks() == null ? "" : request.remarks().trim());
        return HexFormat.of().formatHex(sha256(canonical));
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
