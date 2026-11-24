package com.unispace.identity.application;

import java.time.Instant;

public interface GoogleIdentityVerifier {
    record Proof(String subject, String nonce, Instant issuedAt, Instant expiresAt, String firstName, String lastName) {
        @Override
        public String toString() {
            return "Google proof [redacted]";
        }
    }

    Proof verify(String idToken);

    final class InvalidProof extends RuntimeException {}
    final class VerificationUnavailable extends RuntimeException {}
}
