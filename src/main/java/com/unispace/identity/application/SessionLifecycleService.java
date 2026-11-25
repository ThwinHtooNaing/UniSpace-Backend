package com.unispace.identity.application;

import com.unispace.identity.application.AuthenticationStateService.AccessSession;
import com.unispace.identity.persistence.SessionLifecycleRepository;
import com.unispace.identity.persistence.SessionLifecycleRepository.LockedSession;
import com.unispace.identity.persistence.SessionLifecycleRepository.Reference;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SessionLifecycleService {
    public enum Failure {
        REFRESH_INVALID, REFRESH_TOKEN_REUSED, SESSION_EXPIRED, SESSION_REVOKED, AUTHENTICATION_REQUIRED
    }

    public static final class Rejected extends RuntimeException {
        private final Failure failure;
        public Rejected(Failure failure) { this.failure = failure; }
        public Failure failure() { return failure; }
    }

    private record Authorized(Reference reference, LockedSession session) {}

    private final SessionLifecycleRepository store;
    private final AuthenticationStateService auth;
    private final AccessTokenIssuer access;
    private final RefreshTokens tokens;
    private final TransactionTemplate transaction;

    public SessionLifecycleService(SessionLifecycleRepository store, AuthenticationStateService auth,
                                   AccessTokenIssuer access, RefreshTokens tokens,
                                   PlatformTransactionManager manager) {
        this.store = store;
        this.auth = auth;
        this.access = access;
        this.tokens = tokens;
        this.transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public AuthenticatedSession refresh(String credential) {
        byte[] digest = digest(credential);
        // An empty result means recognized replay. Report it only after family revocation commits.
        return transaction.execute(status -> authorizeRefresh(digest).map(current -> rotate(current, digest)))
                .orElseThrow(() -> new Rejected(Failure.REFRESH_TOKEN_REUSED));
    }

    public void signOut(AccessSession principal) {
        transaction.executeWithoutResult(status -> {
            var ref = new Reference(principal.userId(), principal.sessionId());
            var session = store.lock(ref).orElseThrow(() -> new Rejected(Failure.AUTHENTICATION_REQUIRED));
            requireCurrent(ref, session);
            revoke(new Authorized(ref, session));
        });
    }

    public void signOutWithRefresh(String credential) {
        byte[] digest = digest(credential);
        boolean confirmed = Boolean.TRUE.equals(transaction.execute(status -> {
            var current = authorizeRefresh(digest);
            current.ifPresent(this::revoke);
            return current.isPresent();
        }));
        if (!confirmed) throw new Rejected(Failure.REFRESH_TOKEN_REUSED);
    }

    private Optional<Authorized> authorizeRefresh(byte[] digest) {
        var ref = store.locate(digest).orElseThrow(() -> new Rejected(Failure.REFRESH_INVALID));
        var session = store.lock(ref).orElseThrow(() -> new Rejected(Failure.REFRESH_INVALID));
        // A waiter sees any committed rotation, including one that occurred after locate().
        if (store.consumed(ref.sessionId(), digest)) {
            if (!session.revoked() && !store.revokeFamily(ref)) {
                throw new IllegalStateException("Replay revocation was not confirmed");
            }
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(digest, HexFormat.of().parseHex(session.currentDigest()))) {
            throw new Rejected(Failure.REFRESH_INVALID);
        }
        requireCurrent(ref, session);
        return Optional.of(new Authorized(ref, session));
    }

    private void requireCurrent(Reference ref, LockedSession session) {
        if (session.revoked() || session.deviceId() == null) throw new Rejected(Failure.SESSION_REVOKED);
        var now = store.now();
        if (!session.expiresAt().isAfter(now)) throw new Rejected(Failure.SESSION_EXPIRED);
        if (session.createdAt().isAfter(now) || auth.lockAccessRole(ref.userId(), ref.sessionId()).isEmpty()) {
            if (!session.expiresAt().isAfter(store.now())) throw new Rejected(Failure.SESSION_EXPIRED);
            throw new Rejected(Failure.SESSION_REVOKED);
        }
    }

    private AuthenticatedSession rotate(Authorized current, byte[] digest) {
        var ref = current.reference();
        var now = store.now();
        var expires = now.plusDays(30);
        String replacement = tokens.generate();
        if (!store.rotate(ref, digest, HexFormat.of().formatHex(tokens.digest(replacement)), now, expires)) {
            throw new Rejected(Failure.SESSION_EXPIRED);
        }
        String thumbprint = store.deviceThumbprint(ref, current.session().deviceId());
        return AuthenticatedSession.ready(DeviceCryptography.handle(HexFormat.of().parseHex(thumbprint)),
                access.issueAccess(ref.userId(), ref.sessionId()), replacement, expires.toInstant(ZoneOffset.UTC));
    }

    private void revoke(Authorized current) {
        var ref = current.reference();
        if (!store.revokeFamily(ref) || !store.revokeDevice(ref, current.session().deviceId())) {
            throw new IllegalStateException("Session and device revocation were not confirmed");
        }
    }

    private byte[] digest(String credential) {
        try {
            return tokens.digest(credential);
        } catch (IllegalArgumentException invalid) {
            throw new Rejected(Failure.REFRESH_INVALID);
        }
    }
}
