package com.unispace.identity.persistence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import static java.time.ZoneOffset.UTC;

@Repository
public class GoogleAuthenticationRepository {
    public record Challenge(BigInteger id, byte[] nonceDigest, Instant createdAt) {}
    public record Account(BigInteger id, String status, boolean verifiedEmail) {}

    private final JdbcTemplate jdbc;

    public GoogleAuthenticationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Instant databaseTime() {
        return jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class).toInstant(UTC);
    }

    public void insertChallenge(byte[] idDigest, byte[] nonceDigest, LocalDateTime createdAt,
                                BigInteger user, BigInteger session) {
        jdbc.update("""
                INSERT INTO provider_auth_challenges (challenge_id_digest, provider, purpose, nonce_digest,
                    bound_user_id, bound_session_id, created_at, expires_at)
                VALUES (?, 'GOOGLE', ?, ?, ?, ?, ?, ?)
                """, idDigest, user == null ? "SIGN_IN" : "LINK_PROVIDER", nonceDigest,
                user == null ? null : new BigDecimal(user), session == null ? null : new BigDecimal(session),
                createdAt, createdAt.plusMinutes(5));
    }

    public Optional<Challenge> findSignInChallenge(byte[] idDigest) {
        return jdbc.query("""
                SELECT id, nonce_digest, created_at FROM provider_auth_challenges
                WHERE challenge_id_digest = ? AND provider = 'GOOGLE' AND purpose = 'SIGN_IN'
                  AND bound_user_id IS NULL AND bound_session_id IS NULL AND state_digest IS NULL
                  AND consumed_at IS NULL AND created_at <= UTC_TIMESTAMP(6)
                  AND UTC_TIMESTAMP(6) < expires_at AND UTC_TIMESTAMP(6) < created_at + INTERVAL 5 MINUTE
                """, rows -> rows.next() ? Optional.of(new Challenge(rows.getBigDecimal("id").toBigIntegerExact(),
                rows.getBytes("nonce_digest"), rows.getObject("created_at", LocalDateTime.class).toInstant(UTC)))
                : Optional.empty(), idDigest);
    }

    public boolean consumeSignInChallenge(BigInteger id, byte[] nonceDigest, Instant proofExpiresAt) {
        // Evaluate expiry in the subsequent statement, after waiting for any competing transaction.
        var numericId = new BigDecimal(id);
        boolean exists = !jdbc.queryForList("SELECT id FROM provider_auth_challenges WHERE id = ? FOR UPDATE",
                BigDecimal.class, numericId).isEmpty();
        if (!exists) return false;
        return jdbc.update("""
                UPDATE provider_auth_challenges SET consumed_at = UTC_TIMESTAMP(6)
                WHERE id = ? AND nonce_digest = ? AND provider = 'GOOGLE' AND purpose = 'SIGN_IN'
                  AND bound_user_id IS NULL AND bound_session_id IS NULL AND state_digest IS NULL
                  AND consumed_at IS NULL AND created_at <= UTC_TIMESTAMP(6)
                  AND UTC_TIMESTAMP(6) < expires_at AND UTC_TIMESTAMP(6) < created_at + INTERVAL 5 MINUTE
                  AND UTC_TIMESTAMP(6) < ?
                """, numericId, nonceDigest, LocalDateTime.ofInstant(proofExpiresAt, UTC)) == 1;
    }

    public Optional<BigInteger> findUserId(String subject) {
        return jdbc.query("""
                SELECT user_id FROM provider_identities WHERE provider = 'GOOGLE' AND provider_subject = ?
                """, rows -> rows.next() ? Optional.of(rows.getBigDecimal(1).toBigIntegerExact()) : Optional.empty(), subject);
    }

    public Account lockAccount(BigInteger userId) {
        return jdbc.queryForObject("""
                SELECT status, email_verified_at <= UTC_TIMESTAMP(6) AS verified_email
                FROM users WHERE id = ? FOR UPDATE
                """, (r, row) -> new Account(userId, r.getString("status"), r.getBoolean("verified_email")),
                new BigDecimal(userId));
    }

    public Account insertProvisionalAccount(String firstName, String lastName) {
        var id = insert("""
                INSERT INTO users (first_name, last_name, role, status, created_at, updated_at)
                VALUES (?, ?, 'STUDENT', 'PENDING_PROFILE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, statement -> {
            statement.setString(1, firstName);
            statement.setString(2, lastName);
        });
        return new Account(id, "PENDING_PROFILE", false);
    }

    public void insertIdentity(BigInteger userId, String subject) {
        jdbc.update("""
                INSERT INTO provider_identities (user_id, provider, provider_subject, linked_at)
                VALUES (?, 'GOOGLE', ?, UTC_TIMESTAMP(6))
                """, new BigDecimal(userId), subject);
    }

    public BigInteger insertFlow(BigInteger userId, BigInteger challengeId, byte[] jtiDigest) {
        return insert("""
                INSERT INTO auth_flows (user_id, source_challenge_id, flow_jti_digest, created_at, expires_at)
                SELECT ?, id, ?, consumed_at, consumed_at + INTERVAL 15 MINUTE
                FROM provider_auth_challenges WHERE id = ?
                """, statement -> {
            statement.setBigDecimal(1, new BigDecimal(userId));
            statement.setBytes(2, jtiDigest);
            statement.setBigDecimal(3, new BigDecimal(challengeId));
        });
    }

    private BigInteger insert(String sql, Parameters parameters) {
        var keys = new GeneratedKeyHolder();
        int inserted = jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            parameters.set(statement);
            return statement;
        }, keys);
        if (inserted != 1) throw new IllegalStateException("Expected one authentication row");
        return new BigInteger(keys.getKey().toString());
    }

    @FunctionalInterface
    private interface Parameters {
        void set(PreparedStatement statement) throws SQLException;
    }
}
