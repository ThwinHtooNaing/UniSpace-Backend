package com.unispace.identity.persistence;

import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfSystemProperty(named = "unispace.mysql.tests", matches = "true")
@Testcontainers
class AuthenticationSchemaMigrationTests {
    // Instance scope gives each scenario its own empty database and Flyway history.
    @Container
    final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    private Flyway flyway;
    private JdbcTemplate jdbc;

    @BeforeEach
    void configureFlyway() {
        var database = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        jdbc = new JdbcTemplate(database);
        flyway = Flyway.configure().dataSource(database)
                .locations("classpath:db/migration")
                .target("2")
                .cleanDisabled(true).load();
    }

    @Test
    void migratesFreshDatabaseAndValidatesHistoryOnRerun() {
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                  AND engine = 'InnoDB'
                """, String.class)).containsExactlyInAnyOrder(
                "users", "provider_identities", "trusted_devices", "auth_sessions", "email_challenges",
                "institutional_policies", "role_requests", "channels", "channel_memberships",
                "membership_requests", "files", "upload_sessions", "upload_parts", "video_progress",
                "notifications", "notification_preferences", "audit_events", "outbox_events",
                "provider_auth_challenges", "auth_flows", "device_enrollments", "device_signature_challenges",
                "consumed_refresh_tokens");

        String history = "SELECT * FROM flyway_schema_history ORDER BY installed_rank";
        var applied = jdbc.queryForList(history);
        assertThat(applied).hasSize(2);
        assertThat(applied.get(0)).containsEntry("version", "1").containsEntry("script", "V1__initial_schema.sql")
                .containsEntry("checksum", -1815709415);
        assertThat(applied.get(1)).containsEntry("version", "2")
                .containsEntry("script", "V2__authentication_security.sql");
        assertThat(jdbc.queryForList("""
                SELECT version FROM flyway_schema_history
                WHERE success = 1 AND type = 'SQL' AND checksum IS NOT NULL ORDER BY installed_rank
                """, String.class)).containsExactly("1", "2");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
        assertThat(jdbc.queryForList(history)).isEqualTo(applied);
    }

    @Test
    void upgradesPopulatedV1WithoutConvertingLegacyCredentials() {
        Flyway v1 = Flyway.configure().configuration(flyway.getConfiguration()).target("1").load();
        assertThat(v1.migrate().migrationsExecuted).isOne();
        assertThat(v1.info().current().getVersion().getVersion()).isEqualTo("1");
        seedLegacyAccounts();
        String devices = """
                SELECT id, user_id, device_key_hash, verified_at, last_seen_at, revoked_at
                FROM trusted_devices ORDER BY id
                """;
        String sessions = "SELECT * FROM auth_sessions ORDER BY id";
        String otp = """
                SELECT id, user_id, purpose, destination_email, HEX(code_digest), attempt_count,
                       created_at, expires_at, resend_available_at, consumed_at, invalidated_at
                FROM email_challenges ORDER BY id
                """;
        var deviceRows = jdbc.queryForList(devices);
        var sessionRows = jdbc.queryForList(sessions);
        var otpRows = jdbc.queryForList(otp);
        var v1History = jdbc.queryForMap("SELECT * FROM flyway_schema_history WHERE version = '1'");

        assertThat(flyway.migrate().migrationsExecuted).isOne();
        flyway.validate();
        assertThat(jdbc.queryForMap("SELECT * FROM flyway_schema_history WHERE version = '1'")).isEqualTo(v1History);
        assertThat(jdbc.queryForList(devices)).hasSize(3).isEqualTo(deviceRows);
        assertThat(jdbc.queryForList(sessions)).hasSize(3).isEqualTo(sessionRows);
        assertThat(jdbc.queryForList(otp)).hasSize(1).isEqualTo(otpRows);
        assertThat(jdbc.queryForList("SELECT id FROM trusted_devices WHERE public_key_jwk IS NULL", Long.class))
                .containsExactlyInAnyOrder(11L, 12L, 13L);
        assertThat(jdbc.queryForList("SELECT id FROM email_challenges WHERE auth_flow_id IS NULL", Long.class))
                .containsExactly(31L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumed_refresh_tokens", Long.class)).isZero();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void enforcesProviderChallengeAndFlowConstraints() {
        flyway.migrate();
        seedLegacyAccounts();
        seedFlows();
        jdbc.update("""
                UPDATE provider_auth_challenges SET purpose = 'LINK_PROVIDER', bound_user_id = 1,
                       bound_session_id = 21 WHERE id = 43
                """);
        rejects(1452, "fk_pac_user_session", "UPDATE provider_auth_challenges SET bound_session_id = 22 WHERE id = 43");
        rejects(3819, "ck_pac_binding", "UPDATE provider_auth_challenges SET purpose = 'SIGN_IN' WHERE id = 43");
        rejects(3819, "ck_pac_provider", "UPDATE provider_auth_challenges SET provider = 'UNKNOWN' WHERE id = 43");
        rejects(3819, "ck_pac_expiry", "UPDATE provider_auth_challenges SET expires_at = created_at WHERE id = 43");
        rejects(1062, "uq_pac_challenge_digest", """
                UPDATE provider_auth_challenges SET challenge_id_digest = UNHEX(SHA2('challenge-41', 256)) WHERE id = 43
                """);
        rejects(1062, "uq_pac_nonce_digest", """
                UPDATE provider_auth_challenges SET nonce_digest = UNHEX(SHA2('nonce-41', 256)) WHERE id = 43
                """);
        rejects(1062, "uq_auth_flows_source", "UPDATE auth_flows SET source_challenge_id = 41 WHERE id = 52");
        rejects(1062, "uq_auth_flows_jti", "UPDATE auth_flows SET flow_jti_digest = UNHEX(SHA2('flow-51', 256)) WHERE id = 52");
        rejects(1452, "fk_auth_flows_source", "UPDATE auth_flows SET source_challenge_id = 999 WHERE id = 51");
        rejects(1452, "fk_auth_flows_user", "UPDATE auth_flows SET user_id = 999 WHERE id = 51");
        rejects(3819, "ck_auth_flows_expiry", "UPDATE auth_flows SET expires_at = created_at WHERE id = 51");
        jdbc.update("UPDATE email_challenges SET auth_flow_id = 51 WHERE id = 31");
        rejects(1452, "fk_email_challenges_user_flow", "UPDATE email_challenges SET auth_flow_id = 52 WHERE id = 31");
    }

    @Test
    void bindsEachDeviceChallengeToOneTargetInTheCorrectFlow() {
        flyway.migrate();
        seedLegacyAccounts();
        seedFlows();
        jdbc.update("""
                INSERT INTO device_enrollments (id, auth_flow_id, created_at, expires_at)
                SELECT id + 10, id, created_at, expires_at FROM auth_flows
                """);
        rejects(1062, "uq_device_enrollments_flow", "UPDATE device_enrollments SET auth_flow_id = 51 WHERE id = 62");
        rejects(1452, "fk_device_enrollments_flow", "UPDATE device_enrollments SET auth_flow_id = 999 WHERE id = 61");
        rejects(3819, "ck_device_enrollments_expiry", "UPDATE device_enrollments SET expires_at = created_at WHERE id = 61");
        rejects(3819, "ck_device_enrollments_key_pair", "UPDATE device_enrollments SET public_key_jwk = JSON_OBJECT() WHERE id = 61");
        jdbc.update("""
                INSERT INTO device_signature_challenges
                    (id, user_id, auth_flow_id, device_enrollment_id, trusted_device_id, challenge_nonce, created_at, expires_at)
                VALUES (71, 1, 51, 61, NULL, UNHEX(SHA2('proof-71', 256)), NOW(6), NOW(6) + INTERVAL 2 MINUTE),
                       (72, 1, 51, NULL, 11, UNHEX(SHA2('proof-72', 256)), NOW(6), NOW(6) + INTERVAL 2 MINUTE)
                """);
        rejects(1452, "fk_dsc_user_flow", "UPDATE device_signature_challenges SET user_id = 2 WHERE id = 71");
        rejects(1452, "fk_dsc_enrollment_flow", "UPDATE device_signature_challenges SET device_enrollment_id = 62 WHERE id = 71");
        rejects(1452, "fk_dsc_user_trusted", "UPDATE device_signature_challenges SET trusted_device_id = 12 WHERE id = 72");
        rejects(3819, "ck_dsc_single_target", "UPDATE device_signature_challenges SET trusted_device_id = 11 WHERE id = 71");
        rejects(3819, "ck_dsc_single_target", "UPDATE device_signature_challenges SET device_enrollment_id = NULL WHERE id = 71");
        rejects(3819, "ck_dsc_expiry", "UPDATE device_signature_challenges SET expires_at = created_at WHERE id = 71");
        rejects(1062, "uq_dsc_nonce", "UPDATE device_signature_challenges SET challenge_nonce = UNHEX(SHA2('proof-71', 256)) WHERE id = 72");
    }

    @Test
    void storesDistinctSpentDigestsUnderTheOriginatingSession() {
        flyway.migrate();
        seedLegacyAccounts();
        // Synthetic digest history. This checks representation and lookup, not a refresh service.
        jdbc.update("""
                INSERT INTO consumed_refresh_tokens (session_id, token_digest, consumed_at)
                SELECT id, UNHEX(refresh_token_hash), NOW(6) FROM auth_sessions WHERE id = 21
                """);
        jdbc.update("UPDATE auth_sessions SET refresh_token_hash = SHA2('fixture-successor', 256) WHERE id = 21");
        jdbc.update("""
                INSERT INTO consumed_refresh_tokens (session_id, token_digest, consumed_at)
                VALUES (21, UNHEX(SHA2('fixture-earlier', 256)), NOW(6))
                """);
        assertThat(jdbc.queryForList("""
                SELECT h.session_id FROM consumed_refresh_tokens h JOIN auth_sessions s ON s.id = h.session_id
                WHERE s.device_id = 11 AND s.refresh_token_hash = SHA2('fixture-successor', 256)
                  AND h.token_digest IN (UNHEX(SHA2('session-21', 256)), UNHEX(SHA2('fixture-earlier', 256)))
                """, Long.class)).containsExactly(21L, 21L);
        rejects(1062, "uq_consumed_refresh_digest", """
                INSERT INTO consumed_refresh_tokens (session_id, token_digest, consumed_at)
                VALUES (23, UNHEX(SHA2('session-21', 256)), NOW(6))
                """);
        rejects(1452, "fk_consumed_refresh_session", """
                INSERT INTO consumed_refresh_tokens (session_id, token_digest, consumed_at)
                VALUES (999, UNHEX(SHA2('orphan', 256)), NOW(6))
                """);
        rejects(1451, "fk_consumed_refresh_session", "DELETE FROM auth_sessions WHERE id = 21");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM auth_sessions WHERE id = 23 AND user_id = 1 AND device_id = 13
                   AND refresh_token_hash = SHA2('session-23', 256) AND revoked_at IS NULL
                """, Integer.class)).isOne();
    }

    private void seedLegacyAccounts() {
        jdbc.update("""
                INSERT INTO users (id, role, status, created_at, updated_at)
                VALUES (1, 'STUDENT', 'PENDING_PROFILE', NOW(6), NOW(6)),
                       (2, 'STUDENT', 'PENDING_PROFILE', NOW(6), NOW(6))
                """);
        jdbc.update("""
                INSERT INTO trusted_devices (id, user_id, device_key_hash, verified_at)
                VALUES (11, 1, SHA2('device-11', 256), NOW(6)), (12, 2, SHA2('device-12', 256), NOW(6)),
                       (13, 1, SHA2('device-13', 256), NOW(6))
                """);
        jdbc.update("""
                INSERT INTO auth_sessions (id, user_id, device_id, refresh_token_hash, created_at, expires_at)
                SELECT id + 10, user_id, id, SHA2(CONCAT('session-', id + 10), 256), NOW(6), NOW(6) + INTERVAL 30 DAY
                FROM trusted_devices
                """);
        jdbc.update("""
                INSERT INTO email_challenges (id, user_id, purpose, destination_email, code_digest,
                    attempt_count, created_at, expires_at, resend_available_at)
                VALUES (31, 1, 'REGISTRATION', 'fixture@example.edu', UNHEX(SHA2('fixture-code', 256)),
                    2, NOW(6), NOW(6) + INTERVAL 10 MINUTE, NOW(6) + INTERVAL 1 MINUTE)
                """);
    }

    private void seedFlows() {
        for (int challengeId : new int[] {41, 42, 43}) {
            jdbc.update("""
                    INSERT INTO provider_auth_challenges
                        (id, challenge_id_digest, provider, purpose, nonce_digest, created_at, expires_at)
                    VALUES (?, UNHEX(SHA2(?, 256)), 'GOOGLE', 'SIGN_IN', UNHEX(SHA2(?, 256)), NOW(6), NOW(6) + INTERVAL 5 MINUTE)
                    """, challengeId, "challenge-" + challengeId, "nonce-" + challengeId);
        }
        jdbc.update("""
                INSERT INTO auth_flows (id, user_id, source_challenge_id, flow_jti_digest, created_at, expires_at)
                VALUES (51, 1, 41, UNHEX(SHA2('flow-51', 256)), NOW(6), NOW(6) + INTERVAL 15 MINUTE),
                       (52, 2, 42, UNHEX(SHA2('flow-52', 256)), NOW(6), NOW(6) + INTERVAL 15 MINUTE)
                """);
    }

    private void rejects(int mysqlError, String constraint, String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).rootCause().isInstanceOfSatisfying(SQLException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(mysqlError);
            assertThat(error.getMessage()).contains(constraint);
        });
    }
}
