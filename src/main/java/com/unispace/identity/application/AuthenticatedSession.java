package com.unispace.identity.application;

import java.time.Instant;

public record AuthenticatedSession(String status, String accountStatus, String nextAction, String deviceId,
                                   String accessToken, int expiresInSeconds, String refreshToken, Instant refreshExpiresAt) {
    static AuthenticatedSession ready(String deviceId, String access, String refresh, Instant expiresAt) {
        return new AuthenticatedSession("AUTHENTICATED", "ACTIVE", "READY", deviceId, access, 1200, refresh, expiresAt);
    }

    @Override
    public String toString() {
        return "Authenticated session [redacted]";
    }
}
