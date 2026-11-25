package com.unispace.identity.persistence;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.unispace.identity.application.AuthenticationStateService;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.text.ParseException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuthenticationStateRepository implements AuthenticationStateService {
    private final JdbcTemplate jdbc;

    public AuthenticationStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Role> accessRole(BigInteger userId, BigInteger sessionId) {
        return readAccessRole(userId, sessionId, "");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Role> lockAccessRole(BigInteger userId, BigInteger sessionId) {
        return readAccessRole(userId, sessionId, " FOR SHARE");
    }

    private Optional<Role> readAccessRole(BigInteger userId, BigInteger sessionId, String lockClause) {
        // Use exact numeric parameters for unsigned BIGINT IDs and the database clock for persisted expiry.
        return jdbc.query("""
                SELECT u.status, u.role, u.email_verified_at <= UTC_TIMESTAMP(6) AS email_verified,
                       d.public_key_jwk, d.device_key_hash
                FROM auth_sessions s
                JOIN users u ON u.id = s.user_id
                JOIN trusted_devices d ON d.id = s.device_id AND d.user_id = u.id
                WHERE s.id = ? AND u.id = ? AND s.revoked_at IS NULL
                  AND s.created_at <= UTC_TIMESTAMP(6) AND s.expires_at > UTC_TIMESTAMP(6)
                  AND d.verified_at <= UTC_TIMESTAMP(6) AND d.revoked_at IS NULL AND d.public_key_jwk IS NOT NULL
                """ + lockClause, rows -> {
            if (!rows.next()) return Optional.empty();
            String status = rows.getString("status");
            checkEnabled(status);
            if (!"ACTIVE".equals(status) || !rows.getBoolean("email_verified")
                    || !matchesVerifiedKey(rows.getString("public_key_jwk"), rows.getString("device_key_hash"))) {
                return Optional.empty();
            }
            try {
                return Optional.of(Role.valueOf(rows.getString("role")));
            } catch (IllegalArgumentException unknownRole) {
                return Optional.empty();
            }
        }, new BigDecimal(sessionId), new BigDecimal(userId));
    }

    private static boolean matchesVerifiedKey(String json, String thumbprint) {
        try {
            ECKey key = ECKey.parse(json);
            return Curve.P_256.equals(key.getCurve()) && !key.isPrivate()
                    && (key.getKeyUse() == null || KeyUse.SIGNATURE.equals(key.getKeyUse()))
                    && (key.getKeyOperations() == null || Set.of(KeyOperation.VERIFY).equals(key.getKeyOperations()))
                    && (key.getAlgorithm() == null || JWSAlgorithm.ES256.equals(key.getAlgorithm()))
                    && MessageDigest.isEqual(key.computeThumbprint().decode(), HexFormat.of().parseHex(thumbprint));
        } catch (ParseException | JOSEException | IllegalArgumentException invalidKey) {
            return false;
        }
    }

    @Override
    public Optional<FlowStep> flowStep(BigInteger userId, BigInteger flowId, byte[] jtiDigest) {
        return jdbc.query("""
                SELECT u.status, u.email_verified_at <= UTC_TIMESTAMP(6) AS email_verified,
                       EXISTS (SELECT 1 FROM email_challenges e WHERE e.user_id = f.user_id AND e.auth_flow_id = f.id
                               AND e.purpose = 'REGISTRATION' AND e.invalidated_at IS NULL AND e.consumed_at IS NULL
                               AND e.attempt_count < 5 AND OCTET_LENGTH(e.code_digest) = 32
                               AND e.created_at <= UTC_TIMESTAMP(6)
                               AND e.expires_at > UTC_TIMESTAMP(6)) AS has_registration_code
                FROM auth_flows f
                JOIN users u ON u.id = f.user_id
                JOIN provider_auth_challenges p ON p.id = f.source_challenge_id
                WHERE f.id = ? AND f.user_id = ? AND f.flow_jti_digest = ?
                  AND f.created_at <= UTC_TIMESTAMP(6) AND f.expires_at > UTC_TIMESTAMP(6)
                  AND f.completed_at IS NULL AND f.invalidated_at IS NULL
                  AND p.purpose = 'SIGN_IN' AND p.consumed_at >= p.created_at
                  AND p.consumed_at < p.expires_at AND p.consumed_at < p.created_at + INTERVAL 5 MINUTE
                  AND p.consumed_at <= UTC_TIMESTAMP(6)
                """, rows -> {
            if (!rows.next()) return Optional.empty();
            String status = rows.getString("status");
            checkEnabled(status);
            return switch (status) {
                case "PENDING_PROFILE" -> Optional.of(FlowStep.PROFILE_REQUIRED);
                case "PENDING_EMAIL_VERIFICATION" -> Optional.of(rows.getBoolean("has_registration_code")
                        ? FlowStep.EMAIL_VERIFICATION_REQUIRED : FlowStep.EMAIL_REQUIRED);
                case "ACTIVE" -> !rows.getBoolean("email_verified") ? Optional.empty()
                        : Optional.of(FlowStep.BEGIN_DEVICE_VERIFICATION);
                default -> Optional.empty();
            };
        }, new BigDecimal(flowId), new BigDecimal(userId), jtiDigest);
    }

    private static void checkEnabled(String status) {
        if ("DISABLED".equals(status)) throw new DisabledAccountException();
    }
}
