package com.unispace.identity.persistence;

import com.unispace.identity.application.AuthenticationStateService.RegistrationFlow;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class OnboardingRepository {
    public record Profile(String firstName, String lastName, String studentId) {}
    public record Challenge(String email, byte[] digest, LocalDateTime createdAt, LocalDateTime expiresAt,
                            int attempts, boolean consumed, boolean invalidated) {}
    public record DeliveredCode(BigInteger id, String email, byte[] digest) {}

    private final JdbcTemplate jdbc;

    public OnboardingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lock(RegistrationFlow flow) {
        // A user lock serializes registration writes across every flow owned by that user.
        jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", BigDecimal.class, number(flow.userId()));
        jdbc.queryForList("SELECT id FROM auth_flows WHERE id=? AND user_id=? FOR UPDATE",
                BigDecimal.class, number(flow.flowId()), number(flow.userId()));
    }

    public LocalDateTime now() {
        return jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
    }

    public Profile profile(BigInteger userId) {
        return jdbc.queryForObject("SELECT first_name,last_name,student_id FROM users WHERE id=?",
                (r, n) -> new Profile(r.getString(1), r.getString(2), r.getString(3)), number(userId));
    }

    public void saveProfile(BigInteger userId, Profile profile) {
        requireOne(jdbc.update("""
                UPDATE users SET first_name=?,last_name=?,student_id=?,student_id_normalized=?,
                    status='PENDING_EMAIL_VERIFICATION',updated_at=UTC_TIMESTAMP(6)
                WHERE id=? AND status IN ('PENDING_PROFILE','PENDING_EMAIL_VERIFICATION')
                """, profile.firstName(), profile.lastName(), profile.studentId(), profile.studentId(), number(userId)));
    }

    public Optional<String> pendingEmail(BigInteger userId) {
        return jdbc.query("""
                SELECT destination_email FROM email_challenges
                WHERE user_id=? AND purpose='REGISTRATION' AND OCTET_LENGTH(code_digest)=32
                ORDER BY id DESC LIMIT 1
                """, r -> r.next() ? Optional.of(r.getString(1)) : Optional.empty(), number(userId));
    }

    public boolean coolingDown(BigInteger userId, String purpose, LocalDateTime now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM email_challenges
                WHERE user_id=? AND purpose=? AND resend_available_at>?)
                """, Boolean.class, number(userId), purpose, now));
    }

    public BigInteger reserve(RegistrationFlow flow, String purpose, String email, LocalDateTime now) {
        jdbc.update("""
                UPDATE email_challenges SET invalidated_at=?
                WHERE user_id=? AND purpose=? AND consumed_at IS NULL AND invalidated_at IS NULL
                """, now, number(flow.userId()), purpose);
        var key = new GeneratedKeyHolder();
        // The empty binary value reserves the cooldown. Only an accepted delivery can install a 32-byte digest.
        requireOne(jdbc.update(connection -> {
            var sql = connection.prepareStatement("""
                    INSERT INTO email_challenges (user_id,auth_flow_id,purpose,destination_email,code_digest,
                        created_at,expires_at,resend_available_at)
                    VALUES (?,?,?,?,X'',?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            sql.setBigDecimal(1, number(flow.userId()));
            sql.setBigDecimal(2, number(flow.flowId()));
            sql.setString(3, purpose);
            sql.setString(4, email);
            sql.setObject(5, now);
            sql.setObject(6, now.plusMinutes(10));
            sql.setObject(7, now.plusSeconds(60));
            return sql;
        }, key));
        return new BigInteger(key.getKey().toString());
    }

    public boolean delivered(RegistrationFlow flow, String purpose, BigInteger id, byte[] digest) {
        return jdbc.update("""
                UPDATE email_challenges SET code_digest=?
                WHERE id=? AND user_id=? AND auth_flow_id=? AND purpose=?
                    AND OCTET_LENGTH(code_digest)=0 AND invalidated_at IS NULL AND consumed_at IS NULL
                    AND expires_at>UTC_TIMESTAMP(6)
                """, digest, number(id), number(flow.userId()), number(flow.flowId()), purpose) == 1;
    }

    public void failedDelivery(RegistrationFlow flow, String purpose, BigInteger id) {
        jdbc.update("""
                UPDATE email_challenges SET invalidated_at=UTC_TIMESTAMP(6), resend_available_at=UTC_TIMESTAMP(6)
                WHERE id=? AND user_id=? AND auth_flow_id=? AND purpose=?
                    AND OCTET_LENGTH(code_digest)=0 AND invalidated_at IS NULL
                """, number(id), number(flow.userId()), number(flow.flowId()), purpose);
    }

    public Optional<Challenge> challenge(RegistrationFlow flow, String purpose, BigInteger id) {
        return jdbc.query("""
                SELECT destination_email,code_digest,created_at,expires_at,attempt_count,consumed_at,invalidated_at
                FROM email_challenges WHERE id=? AND user_id=? AND auth_flow_id=? AND purpose=?
                """, r -> r.next() ? Optional.of(new Challenge(r.getString(1), r.getBytes(2),
                r.getObject(3, LocalDateTime.class), r.getObject(4, LocalDateTime.class), r.getInt(5),
                r.getObject(6) != null, r.getObject(7) != null)) : Optional.empty(),
                number(id), number(flow.userId()), number(flow.flowId()), purpose);
    }

    public Optional<BigInteger> latestChallenge(RegistrationFlow flow, String purpose) {
        return jdbc.query("""
                SELECT id FROM email_challenges
                WHERE user_id=? AND auth_flow_id=? AND purpose=?
                ORDER BY id DESC LIMIT 1
                """, r -> r.next() ? Optional.of(r.getBigDecimal(1).toBigIntegerExact()) : Optional.empty(),
                number(flow.userId()), number(flow.flowId()), purpose);
    }

    public List<DeliveredCode> deliveredCodes(RegistrationFlow flow, String purpose) {
        return jdbc.query("""
                SELECT id,destination_email,code_digest FROM email_challenges
                WHERE user_id=? AND auth_flow_id=? AND purpose=? AND OCTET_LENGTH(code_digest)=32
                """, (r, index) -> new DeliveredCode(r.getBigDecimal(1).toBigIntegerExact(), r.getString(2), r.getBytes(3)),
                number(flow.userId()), number(flow.flowId()), purpose);
    }

    public void consume(BigInteger id, LocalDateTime now) {
        requireOne(jdbc.update("UPDATE email_challenges SET consumed_at=? WHERE id=? AND consumed_at IS NULL",
                now, number(id)));
    }

    public void recordFailure(BigInteger id, int attempts, LocalDateTime now) {
        requireOne(jdbc.update("UPDATE email_challenges SET attempt_count=?,invalidated_at=? WHERE id=?",
                attempts, attempts == 5 ? now : null, number(id)));
    }

    public void activate(RegistrationFlow flow, BigInteger id, String email, String normalizedEmail, LocalDateTime now) {
        requireOne(jdbc.update("""
                UPDATE users SET university_email=?,email_normalized=?,email_verified_at=?,status='ACTIVE',updated_at=?
                WHERE id=? AND status='PENDING_EMAIL_VERIFICATION'
                """, email, normalizedEmail, now, now, number(flow.userId())));
        consume(id, now);
    }

    public void establishEnrollment(RegistrationFlow flow) {
        requireOne(jdbc.update("""
                INSERT INTO device_enrollments (auth_flow_id,authorization_verified_at,created_at,expires_at)
                SELECT id,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),expires_at FROM auth_flows
                WHERE id=? AND user_id=? AND expires_at>UTC_TIMESTAMP(6)
                """, number(flow.flowId()), number(flow.userId())));
    }

    private static void requireOne(int rows) {
        if (rows != 1) throw new IllegalStateException("Registration write did not affect one row");
    }

    private static BigDecimal number(BigInteger id) {
        return new BigDecimal(id);
    }
}
