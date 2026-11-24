package com.unispace.identity.application;

import com.unispace.identity.persistence.GoogleAuthenticationRepository;
import com.unispace.identity.persistence.GoogleAuthenticationRepository.Account;
import com.unispace.identity.persistence.GoogleAuthenticationRepository.Challenge;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static java.time.ZoneOffset.UTC;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "unispace.auth.google", name = "audience")
public class GoogleSignInService {
    public record ChallengeResponse(String challengeId, String nonce, Instant expiresAt) {
        @Override
        public String toString() { return "Provider challenge [redacted]"; }
    }

    public record FlowResponse(String status, String accountStatus, String nextAction, String flowToken,
                               int expiresInSeconds) {
        @Override
        public String toString() { return "Authentication flow [redacted]"; }
    }

    public enum Failure {
        AUTH_CHALLENGE_INVALID_OR_EXPIRED, AUTH_NONCE_MISMATCH, AUTH_PROVIDER_INVALID,
        AUTH_PROVIDER_UNAVAILABLE, AUTHENTICATION_REQUIRED, ACCESS_DENIED, ACCOUNT_DISABLED
    }

    public static final class Rejected extends RuntimeException {
        private final Failure failure;
        public Rejected(Failure failure) { this.failure = failure; }
        public Failure failure() { return failure; }
    }

    private static final int MAX_TRANSACTION_ATTEMPTS = 3;
    private final SecureRandom random = new SecureRandom();
    private final GoogleIdentityVerifier google;
    private final GoogleAuthenticationRepository store;
    private final AuthenticationStateService state;
    private final RestrictedFlowTokens tokens;
    private final TransactionTemplate transaction;

    public GoogleSignInService(GoogleIdentityVerifier google, GoogleAuthenticationRepository store,
                               AuthenticationStateService state, RestrictedFlowTokens tokens,
                               PlatformTransactionManager transactionManager) {
        this.google = google;
        this.store = store;
        this.state = state;
        this.tokens = tokens;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public ChallengeResponse challenge(AuthenticationStateService.AccessSession linkSession) {
        String id = randomSecret();
        String nonce = randomSecret();
        return transaction.execute(status -> {
            BigInteger user = null;
            BigInteger session = null;
            if (linkSession != null) {
                user = linkSession.userId();
                session = linkSession.sessionId();
                if (state.lockAccessRole(user, session).isEmpty()) throw new Rejected(Failure.ACCESS_DENIED);
            }
            Instant now = store.databaseTime();
            store.insertChallenge(digest(id), digest(nonce), LocalDateTime.ofInstant(now, UTC), user, session);
            return new ChallengeResponse(id, nonce, now.plusSeconds(300));
        });
    }

    public FlowResponse exchange(String challengeId, String idToken) {
        var challenge = store.findSignInChallenge(digest(challengeId)).orElseThrow(GoogleSignInService::invalidChallenge);
        GoogleIdentityVerifier.Proof proof;
        try {
            proof = google.verify(idToken);
        } catch (GoogleIdentityVerifier.InvalidProof e) {
            throw new Rejected(Failure.AUTH_PROVIDER_INVALID);
        } catch (GoogleIdentityVerifier.VerificationUnavailable e) {
            throw new Rejected(Failure.AUTH_PROVIDER_UNAVAILABLE);
        }
        byte[] nonceDigest = digest(proof.nonce());
        if (!MessageDigest.isEqual(challenge.nonceDigest(), nonceDigest)) throw new Rejected(Failure.AUTH_NONCE_MISMATCH);
        if (proof.issuedAt().isBefore(challenge.createdAt().minusSeconds(30))) throw new Rejected(Failure.AUTH_PROVIDER_INVALID);

        // A competing first sign-in may win the provider-subject unique key. Retry only after rollback.
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.execute(status -> finishExchange(challenge, proof, nonceDigest));
            } catch (IdentityAlreadyInserted | PessimisticLockingFailureException e) {
                if (attempt == MAX_TRANSACTION_ATTEMPTS) throw new Rejected(Failure.AUTH_PROVIDER_UNAVAILABLE);
            }
        }
    }

    private FlowResponse finishExchange(Challenge challenge, GoogleIdentityVerifier.Proof proof, byte[] nonceDigest) {
        if (!store.consumeSignInChallenge(challenge.id(), nonceDigest, proof.expiresAt())) throw invalidChallenge();

        Account account = store.findUserId(proof.subject()).map(store::lockAccount)
                .orElseGet(() -> provision(proof));
        String action = switch (account.status()) {
            case "PENDING_PROFILE" -> "PROFILE_REQUIRED";
            case "PENDING_EMAIL_VERIFICATION" -> "EMAIL_REQUIRED";
            case "ACTIVE" -> {
                if (!account.verifiedEmail()) throw new Rejected(Failure.ACCESS_DENIED);
                // Provider exchange has no device handle. Device-challenges will determine proof or OTP.
                yield "DEVICE_PROOF_REQUIRED";
            }
            case "DISABLED" -> throw new Rejected(Failure.ACCOUNT_DISABLED);
            default -> throw new Rejected(Failure.ACCESS_DENIED);
        };
        var pending = tokens.prepareFlow();
        var flowId = store.insertFlow(account.id(), challenge.id(), pending.jtiDigest());
        return new FlowResponse("ACTIVE".equals(account.status()) ? "DEVICE_VERIFICATION_REQUIRED" : "ONBOARDING_REQUIRED",
                account.status(), action, pending.sign().apply(account.id(), flowId), 900);
    }

    private Account provision(GoogleIdentityVerifier.Proof proof) {
        Account account = store.insertProvisionalAccount(proof.firstName(), proof.lastName());
        try {
            store.insertIdentity(account.id(), proof.subject());
        } catch (DuplicateKeyException e) {
            throw new IdentityAlreadyInserted();
        }
        return account;
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static Rejected invalidChallenge() {
        return new Rejected(Failure.AUTH_CHALLENGE_INVALID_OR_EXPIRED);
    }

    private static final class IdentityAlreadyInserted extends RuntimeException {}
}
