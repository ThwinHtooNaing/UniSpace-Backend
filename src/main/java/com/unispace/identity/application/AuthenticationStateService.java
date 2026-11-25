package com.unispace.identity.application;

import java.math.BigInteger;
import java.security.Principal;
import java.util.Optional;

/** Current authorization facts for a verified credential. Empty means the binding is no longer usable. */
public interface AuthenticationStateService {
    record AccessSession(BigInteger userId, BigInteger sessionId) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }
    record RegistrationFlow(BigInteger userId, BigInteger flowId, byte[] jtiDigest) implements Principal {
        @Override
        public String getName() {
            return userId.toString();
        }
    }
    enum Role { STUDENT, SUB_MANAGEMENT, MANAGEMENT, ADMIN }

    // An ACTIVE account can begin device verification; individual proof operations need a bound challenge.
    enum FlowStep { PROFILE_REQUIRED, EMAIL_REQUIRED, EMAIL_VERIFICATION_REQUIRED, BEGIN_DEVICE_VERIFICATION }

    final class DisabledAccountException extends RuntimeException {}

    Optional<Role> accessRole(BigInteger userId, BigInteger sessionId);

    Optional<Role> lockAccessRole(BigInteger userId, BigInteger sessionId);

    Optional<FlowStep> flowStep(BigInteger userId, BigInteger flowId, byte[] jtiDigest);
}
