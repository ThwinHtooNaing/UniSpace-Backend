package com.unispace.audit.application;

import java.math.BigInteger;
import java.util.UUID;

public interface AuditWriter {
    enum Action {
        ROLE_CHANGED("USER"),
        ROLE_REQUEST_DECIDED("ROLE_REQUEST"),
        MEMBERSHIP_REQUEST_DECIDED("MEMBERSHIP_REQUEST"),
        CHANNEL_RENAMED("CHANNEL"),
        CHANNEL_DELETED("CHANNEL"),
        CHANNEL_OWNER_REMOVED("CHANNEL"),
        INSTITUTIONAL_POLICY_CHANGED("INSTITUTIONAL_POLICY");

        private final String targetType;

        Action(String targetType) {
            this.targetType = targetType;
        }

        public String targetType() {
            return targetType;
        }
    }

    /** Record an authenticated actor's action in the caller's transaction. */
    void record(BigInteger actorUserId, Action action, BigInteger targetId, UUID correlationId);
}
