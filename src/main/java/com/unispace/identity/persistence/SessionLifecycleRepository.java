package com.unispace.identity.persistence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionLifecycleRepository {
    public record Reference(BigInteger userId, BigInteger sessionId) {}
    public record LockedSession(BigInteger deviceId, String currentDigest, LocalDateTime createdAt,
                                LocalDateTime expiresAt, boolean revoked) {}

    private final JdbcTemplate jdbc;

    public SessionLifecycleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Reference> locate(byte[] digest) {
        // Resolve either index in one snapshot. The digest and current authority are checked again under lock.
        return jdbc.query("""
                SELECT user_id,id FROM auth_sessions
                WHERE id=COALESCE((SELECT session_id FROM consumed_refresh_tokens WHERE token_digest=?),
                                  (SELECT id FROM auth_sessions WHERE refresh_token_hash=?))
                """, r -> r.next() ? Optional.of(new Reference(r.getBigDecimal(1).toBigIntegerExact(),
                r.getBigDecimal(2).toBigIntegerExact())) : Optional.empty(), digest, HexFormat.of().formatHex(digest));
    }

    public Optional<LockedSession> lock(Reference ref) {
        // Device enrollment and onboarding also lock the user before child state.
        if (jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", BigDecimal.class,
                number(ref.userId())).isEmpty()) return Optional.empty();
        return jdbc.query("""
                SELECT device_id,refresh_token_hash,created_at,expires_at,revoked_at
                FROM auth_sessions WHERE id=? AND user_id=? FOR UPDATE
                """, r -> {
            if (!r.next()) return Optional.empty();
            var device = r.getBigDecimal(1);
            return Optional.of(new LockedSession(device == null ? null : device.toBigIntegerExact(), r.getString(2),
                    r.getObject(3, LocalDateTime.class), r.getObject(4, LocalDateTime.class), r.getObject(5) != null));
        }, number(ref.sessionId()), number(ref.userId()));
    }

    public boolean consumed(BigInteger session, byte[] digest) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM consumed_refresh_tokens WHERE token_digest=? AND session_id=?)
                """, Boolean.class, digest, number(session)));
    }

    public LocalDateTime now() {
        return jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
    }

    public String deviceThumbprint(Reference ref, BigInteger device) {
        return jdbc.queryForObject("SELECT device_key_hash FROM trusted_devices WHERE id=? AND user_id=?",
                String.class, number(device), number(ref.userId()));
    }

    public boolean rotate(Reference ref, byte[] spent, String replacement, LocalDateTime now, LocalDateTime expires) {
        int changed = jdbc.update("""
                UPDATE auth_sessions SET refresh_token_hash=?,last_refreshed_at=?,expires_at=?
                WHERE id=? AND user_id=? AND refresh_token_hash=? AND revoked_at IS NULL AND expires_at>UTC_TIMESTAMP(6)
                """, replacement, now, expires, number(ref.sessionId()), number(ref.userId()), HexFormat.of().formatHex(spent));
        if (changed != 1) return false;
        jdbc.update("INSERT INTO consumed_refresh_tokens (session_id,token_digest,consumed_at) VALUES (?,?,?)",
                number(ref.sessionId()), spent, now);
        return true;
    }

    public boolean revokeFamily(Reference ref) {
        return jdbc.update("""
                UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP(6)
                WHERE id=? AND user_id=? AND revoked_at IS NULL
                """, number(ref.sessionId()), number(ref.userId())) == 1;
    }

    public boolean revokeDevice(Reference ref, BigInteger device) {
        return jdbc.update("""
                UPDATE trusted_devices SET revoked_at=UTC_TIMESTAMP(6)
                WHERE id=? AND user_id=? AND revoked_at IS NULL
                """, number(device), number(ref.userId())) == 1;
    }

    private static BigDecimal number(BigInteger id) { return new BigDecimal(id); }
}
