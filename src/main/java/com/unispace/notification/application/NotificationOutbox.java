package com.unispace.notification.application;

import java.math.BigInteger;
import java.util.UUID;

public interface NotificationOutbox {
    enum Kind {
        ROLE_REQUEST_SUBMITTED("ROLE_UPDATES", "ROLE_REQUEST"),
        ROLE_REQUEST_DECIDED("ROLE_UPDATES", "ROLE_REQUEST"),
        ROLE_CHANGED("ROLE_UPDATES", "USER"),
        EMPTY_CHANNEL_REQUESTED("CHANNEL_REQUESTS", "MEMBERSHIP_REQUEST"),
        MEMBERSHIP_REQUEST_DECIDED("CHANNEL_REQUESTS", "MEMBERSHIP_REQUEST"),
        MEMBERSHIP_REQUEST_CANCELLED("CHANNEL_REQUESTS", "MEMBERSHIP_REQUEST"),
        CHANNEL_OWNER_REMOVED("CHANNEL_REQUESTS", "CHANNEL");

        private final String category;
        private final String targetType;

        Kind(String category, String targetType) {
            this.category = category;
            this.targetType = targetType;
        }

        public String category() {
            return category;
        }

        public String targetType() {
            return targetType;
        }
    }

    record Notice(Kind kind, BigInteger recipientId, BigInteger targetId) {
        public Notice {
            if (kind == null || !isId(recipientId) || !isId(targetId)) {
                throw new IllegalArgumentException("Invalid inbox notification");
            }
        }

        private static boolean isId(BigInteger id) {
            return id != null && id.signum() > 0 && id.bitLength() <= Long.SIZE;
        }
    }

    final class ConflictingChangeException extends RuntimeException {
        public ConflictingChangeException() {
            super("Conflicting outbox change ID");
        }
    }

    /** Use a stable server-generated change ID across retries, in the business transaction. */
    void enqueue(UUID changeId, Notice notice);
}
