package com.unispace.identity.persistence;

import com.unispace.identity.application.AuthenticationStateService.RegistrationFlow;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceAuthenticationRepository {
    public record ActiveFlow(String email, LocalDateTime expiresAt) {}
    public record Enrollment(BigInteger id, boolean verified, String jwk, byte[] thumbprint) {}
    public record TrustedDevice(BigInteger id, String hash, String jwk) {}
    public record Proof(BigInteger enrollmentId, BigInteger deviceId, byte[] nonce, LocalDateTime expiresAt) {}

    private final JdbcTemplate jdbc;

    public DeviceAuthenticationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ActiveFlow> activeFlow(RegistrationFlow flow) {
        return jdbc.query("""
                SELECT u.university_email,f.expires_at
                FROM auth_flows f JOIN users u ON u.id=f.user_id
                WHERE f.id=? AND u.id=? AND u.status='ACTIVE' AND u.email_verified_at<=UTC_TIMESTAMP(6)
                  AND u.university_email IS NOT NULL
                """, r -> r.next() ? Optional.of(new ActiveFlow(r.getString(1),r.getObject(2, LocalDateTime.class)))
                : Optional.empty(), number(flow.flowId()),number(flow.userId()));
    }

    public Optional<Enrollment> enrollment(RegistrationFlow flow) {
        return jdbc.query("""
                SELECT id, authorization_verified_at<=UTC_TIMESTAMP(6), public_key_jwk, public_key_thumbprint
                FROM device_enrollments WHERE auth_flow_id=?
                  AND expires_at>UTC_TIMESTAMP(6) AND invalidated_at IS NULL AND completed_at IS NULL
                FOR UPDATE
                """, r -> r.next() ? Optional.of(new Enrollment(r.getBigDecimal(1).toBigIntegerExact(),
                r.getBoolean(2),r.getString(3),r.getBytes(4))) : Optional.empty(),number(flow.flowId()));
    }

    public BigInteger createEnrollment(RegistrationFlow flow, LocalDateTime now, LocalDateTime expiry) {
        return insert("INSERT INTO device_enrollments(auth_flow_id,created_at,expires_at) VALUES (?,?,?)", sql -> {
            sql.setBigDecimal(1,number(flow.flowId()));
            sql.setObject(2,now);
            sql.setObject(3,expiry);
        });
    }

    public void authorizeEnrollment(BigInteger id, LocalDateTime now) {
        one(jdbc.update("""
                UPDATE device_enrollments SET authorization_verified_at=?
                WHERE id=? AND authorization_verified_at IS NULL AND expires_at>? AND invalidated_at IS NULL AND completed_at IS NULL
                """, now,number(id),now));
    }

    public void registerKey(BigInteger enrollment, String publicJwk, byte[] thumbprint) {
        one(jdbc.update("""
                UPDATE device_enrollments SET public_key_jwk=?,public_key_thumbprint=?
                WHERE id=? AND public_key_jwk IS NULL
                """, publicJwk,thumbprint,number(enrollment)));
    }

    public boolean knownKey(BigInteger user, String hash) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM trusted_devices WHERE user_id=? AND device_key_hash=?)
                """,Boolean.class,number(user),hash));
    }

    public Optional<TrustedDevice> trusted(RegistrationFlow flow, BigInteger id, String hash) {
        if ((id == null) == (hash == null)) throw new IllegalArgumentException("Expected one device locator");
        String query = """
                SELECT id,device_key_hash,public_key_jwk FROM trusted_devices
                WHERE user_id=? AND verified_at<=UTC_TIMESTAMP(6) AND revoked_at IS NULL AND public_key_jwk IS NOT NULL
                """ + (id == null ? " AND device_key_hash=?" : " AND id=?") + " FOR UPDATE";
        return jdbc.query(query, r -> r.next() ? Optional.of(new TrustedDevice(r.getBigDecimal(1).toBigIntegerExact(),
                r.getString(2),r.getString(3))) : Optional.empty(),number(flow.userId()), id == null ? hash : number(id));
    }

    public BigInteger createProof(RegistrationFlow flow, BigInteger enrollment, BigInteger device, byte[] nonce,
                                  LocalDateTime now, LocalDateTime expiry) {
        return insert("""
                INSERT INTO device_signature_challenges(user_id,auth_flow_id,device_enrollment_id,trusted_device_id,
                    challenge_nonce,created_at,expires_at) VALUES (?,?,?,?,?,?,?)
                """, sql -> {
            sql.setBigDecimal(1,number(flow.userId()));
            sql.setBigDecimal(2,number(flow.flowId()));
            sql.setBigDecimal(3,number(enrollment));
            sql.setBigDecimal(4,number(device));
            sql.setBytes(5,nonce);
            sql.setObject(6,now);
            sql.setObject(7,expiry);
        });
    }

    public Optional<Proof> liveProof(RegistrationFlow flow, BigInteger id) {
        return jdbc.query("""
                SELECT device_enrollment_id,trusted_device_id,challenge_nonce,expires_at
                FROM device_signature_challenges
                WHERE id=? AND user_id=? AND auth_flow_id=? AND consumed_at IS NULL
                  AND created_at<=UTC_TIMESTAMP(6) AND expires_at>UTC_TIMESTAMP(6)
                  AND UTC_TIMESTAMP(6)<created_at+INTERVAL 2 MINUTE
                FOR UPDATE
                """, r -> r.next() ? Optional.of(new Proof(integer(r.getBigDecimal(1)),integer(r.getBigDecimal(2)),
                r.getBytes(3),r.getObject(4,LocalDateTime.class))) : Optional.empty(),
                number(id),number(flow.userId()),number(flow.flowId()));
    }

    public boolean consumeProof(BigInteger id) {
        return jdbc.update("""
                UPDATE device_signature_challenges SET consumed_at=UTC_TIMESTAMP(6)
                WHERE id=? AND consumed_at IS NULL AND expires_at>UTC_TIMESTAMP(6)
                  AND UTC_TIMESTAMP(6)<created_at+INTERVAL 2 MINUTE
                """,number(id)) == 1;
    }

    public BigInteger createTrust(RegistrationFlow flow, BigInteger enrollment, String hash, String jwk,
                                  LocalDateTime now) {
        one(jdbc.update("""
                UPDATE device_enrollments SET completed_at=? WHERE id=? AND completed_at IS NULL
                """,now,number(enrollment)));
        return insert("""
                INSERT INTO trusted_devices(user_id,device_key_hash,public_key_jwk,verified_at,last_seen_at)
                VALUES (?,?,?,?,?)
                """, sql -> {
            sql.setBigDecimal(1,number(flow.userId()));
            sql.setString(2,hash);
            sql.setString(3,jwk);
            sql.setObject(4,now);
            sql.setObject(5,now);
        });
    }

    public void recordSeen(BigInteger id, LocalDateTime now) {
        one(jdbc.update("UPDATE trusted_devices SET last_seen_at=? WHERE id=? AND revoked_at IS NULL",
                now,number(id)));
    }

    public BigInteger createSession(RegistrationFlow flow, BigInteger device, String digest,
                                    LocalDateTime now, LocalDateTime expires) {
        one(jdbc.update("""
                UPDATE auth_flows SET completed_at=UTC_TIMESTAMP(6)
                WHERE id=? AND user_id=? AND completed_at IS NULL AND invalidated_at IS NULL
                    AND expires_at>UTC_TIMESTAMP(6)
                """,number(flow.flowId()),number(flow.userId())));
        return insert("""
                INSERT INTO auth_sessions(user_id,device_id,refresh_token_hash,created_at,expires_at)
                VALUES (?,?,?,?,?)
                """, sql -> {
            sql.setBigDecimal(1,number(flow.userId()));
            sql.setBigDecimal(2,number(device));
            sql.setString(3,digest);
            sql.setObject(4,now);
            sql.setObject(5,expires);
        });
    }

    private BigInteger insert(String statement, Parameters parameters) {
        var keys = new GeneratedKeyHolder();
        one(jdbc.update(connection -> {
            var sql = connection.prepareStatement(statement, Statement.RETURN_GENERATED_KEYS);
            parameters.set(sql);
            return sql;
        },keys));
        return new BigInteger(keys.getKey().toString());
    }

    private static void one(int count) {
        if (count != 1) throw new IllegalStateException("Device state write did not affect one row");
    }

    private static BigDecimal number(BigInteger id) {
        return id == null ? null : new BigDecimal(id);
    }

    private static BigInteger integer(BigDecimal id) {
        return id == null ? null : id.toBigIntegerExact();
    }

    @FunctionalInterface
    private interface Parameters {
        void set(PreparedStatement statement) throws SQLException;
    }
}
