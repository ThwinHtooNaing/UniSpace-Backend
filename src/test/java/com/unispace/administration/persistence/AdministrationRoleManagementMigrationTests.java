package com.unispace.administration.persistence;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.unispace.identity.application.AuthenticationStateService.Role;
import com.unispace.identity.persistence.AuthenticationStateRepository;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
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
class AdministrationRoleManagementMigrationTests {
    private static final String HISTORY = "SELECT * FROM flyway_schema_history ORDER BY installed_rank";
    private static final int V1_CHECKSUM = -1815709415;
    private static final int V2_CHECKSUM = 2130202537;
    private static final String GUARD_LOCK = "SELECT HEX(policy_seed_operation_id) FROM administration_guard WHERE id=1";
    private static final String SEED_RECEIPT = """
            UPDATE administration_guard SET
                policy_seed_operation_id=UNHEX('0102030405060708090A0B0C0D0E0F10'),
                policy_seeded_at=UTC_TIMESTAMP(6)
            WHERE id=1
            """;
    private static final String REPLAY_HISTORY = """
            SELECT id,session_id,HEX(token_digest),consumed_at FROM consumed_refresh_tokens
            """;
    private static final List<String> PRESERVED_DATA = List.of(
            """
            SELECT id,first_name,last_name,student_id,student_id_normalized,university_email,email_normalized,
                   email_verified_at,role,status,created_at,updated_at FROM users ORDER BY id
            """,
            "SELECT * FROM provider_identities ORDER BY id",
            "SELECT * FROM trusted_devices ORDER BY id",
            """
            SELECT id,user_id,device_id,refresh_token_hash,created_at,last_refreshed_at,expires_at,revoked_at
            FROM auth_sessions ORDER BY id
            """,
            "SELECT * FROM institutional_policies ORDER BY id",
            """
            SELECT id,HEX(challenge_id_digest),provider,purpose,HEX(nonce_digest),HEX(state_digest),bound_user_id,
                   bound_session_id,created_at,expires_at,consumed_at FROM provider_auth_challenges ORDER BY id
            """,
            """
            SELECT id,user_id,source_challenge_id,HEX(flow_jti_digest),created_at,expires_at,completed_at,invalidated_at
            FROM auth_flows ORDER BY id
            """,
            """
            SELECT id,user_id,purpose,destination_email,HEX(code_digest),attempt_count,created_at,expires_at,
                   resend_available_at,consumed_at,invalidated_at,auth_flow_id FROM email_challenges ORDER BY id
            """,
            REPLAY_HISTORY + "ORDER BY id"
    );

    @Container
    final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    private DriverManagerDataSource database;
    private JdbcTemplate jdbc;

    @BeforeEach
    void configureDatabase() {
        database = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        jdbc = new JdbcTemplate(database);
    }

    @Test
    void migratesFreshDatabaseAndValidatesHistoryOnRerun() {
        var v3 = flyway("3");
        assertThat(v3.migrate().migrationsExecuted).isEqualTo(3);
        v3.validate();
        var history = jdbc.queryForList(HISTORY);
        assertThat(history).hasSize(3);
        assertThat(history.get(0)).containsEntry("version", "1").containsEntry("checksum", V1_CHECKSUM);
        assertThat(history.get(1)).containsEntry("version", "2").containsEntry("checksum", V2_CHECKSUM);
        assertThat(history.get(2)).containsEntry("version", "3")
                .containsEntry("script", "V3__administration_role_management.sql").containsEntry("success", true);
        assertThat(v3.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(v3.info().pending()).isEmpty();
        assertThat(v3.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList(HISTORY)).isEqualTo(history);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM institutional_policies", Long.class)).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM administration_guard", Integer.class)).containsExactly(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM administration_guard
                WHERE policy_seed_operation_id IS NULL AND policy_seeded_at IS NULL
                  AND first_admin_operation_id IS NULL AND first_admin_user_id IS NULL
                  AND first_admin_provisioned_at IS NULL
                """, Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.statistics
                WHERE table_schema=DATABASE() AND table_name='users' AND index_name='ix_users_admin_eligibility'
                ORDER BY seq_in_index
                """, String.class)).containsExactly("role", "status", "email_verified_at");
        jdbc.update("""
                INSERT INTO users (role,status,created_at,updated_at)
                VALUES ('STUDENT','PENDING_PROFILE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        assertThat(jdbc.queryForObject("SELECT role_version FROM users", Long.class)).isZero();
        rejects(1264, "role_version", "UPDATE users SET role_version=-1");
    }

    @Test
    void upgradesPopulatedV2AndPreservesExistingSessionsAndPolicyAttribution() throws Exception {
        var v2 = flyway("2");
        assertThat(v2.migrate().migrationsExecuted).isEqualTo(2);
        seedExistingData();
        var history = jdbc.queryForList(HISTORY);
        var existing = PRESERVED_DATA.stream().map(jdbc::queryForList).toList();
        var authentication = new AuthenticationStateRepository(jdbc);
        assertThat(authentication.accessRole(BigInteger.ONE, BigInteger.valueOf(21))).contains(Role.STUDENT);

        var v3 = flyway("3");
        assertThat(v3.migrate().migrationsExecuted).isOne();
        v3.validate();
        assertThat(jdbc.queryForList(HISTORY).subList(0, 2)).isEqualTo(history);
        assertThat(PRESERVED_DATA.stream().map(jdbc::queryForList).toList()).isEqualTo(existing);
        assertThat(jdbc.queryForList("SELECT role_version FROM users ORDER BY id", Long.class)).containsExactly(0L, 0L);
        assertThat(jdbc.queryForList("SELECT id FROM auth_sessions WHERE source_auth_flow_id IS NULL ORDER BY id",
                Long.class)).containsExactly(21L, 22L);
        assertThat(authentication.accessRole(BigInteger.ONE, BigInteger.valueOf(21))).contains(Role.STUDENT);
        assertThat(authentication.accessRole(BigInteger.ONE, BigInteger.valueOf(22))).contains(Role.STUDENT);
        assertThat(v3.migrate().migrationsExecuted).isZero();

        rejects(1452, "fk_policies_updated_by", "UPDATE institutional_policies SET updated_by_user_id=99 WHERE id=1");
        assertThat(jdbc.update("UPDATE institutional_policies SET updated_by_user_id=NULL WHERE id=1")).isOne();
        assertThat(jdbc.queryForObject("SELECT updated_by_user_id FROM institutional_policies WHERE id=1",
                Long.class)).isNull();
        assertThat(jdbc.update("UPDATE institutional_policies SET updated_by_user_id=2 WHERE id=1")).isOne();
        assertThat(jdbc.queryForMap("""
                SELECT referenced_table_name,delete_rule FROM information_schema.referential_constraints
                WHERE constraint_schema=DATABASE() AND table_name='institutional_policies'
                  AND constraint_name='fk_policies_updated_by'
                """)).containsEntry("referenced_table_name", "users").containsEntry("delete_rule", "RESTRICT");
    }

    @Test
    void enforcesSingletonAndCompleteBootstrapReceipts() {
        flyway("3").migrate();
        rejects(1062, "PRIMARY", "INSERT INTO administration_guard (id) VALUES (1)");
        rejects(3819, "ck_administration_guard_singleton", "INSERT INTO administration_guard (id) VALUES (2)");
        rejects(3819, "ck_administration_guard_policy_receipt", """
                UPDATE administration_guard SET policy_seed_operation_id=UNHEX(REPEAT('01',16)) WHERE id=1
                """);
        rejects(3819, "ck_administration_guard_policy_receipt", """
                UPDATE administration_guard SET policy_seeded_at=UTC_TIMESTAMP(6) WHERE id=1
                """);
        rejects(3819, "ck_administration_guard_admin_receipt", """
                UPDATE administration_guard SET first_admin_operation_id=UNHEX(REPEAT('02',16)),
                    first_admin_provisioned_at=UTC_TIMESTAMP(6) WHERE id=1
                """);
        rejects(3819, "ck_administration_guard_admin_receipt", """
                UPDATE administration_guard SET first_admin_operation_id=UNHEX(REPEAT('02',16)), first_admin_user_id=0,
                    first_admin_provisioned_at=UTC_TIMESTAMP(6) WHERE id=1
                """);
        assertThat(jdbc.update(SEED_RECEIPT)).isOne();
        assertThat(jdbc.update("""
                UPDATE administration_guard SET first_admin_operation_id=UNHEX(REPEAT('02',16)), first_admin_user_id=42,
                    first_admin_provisioned_at=UTC_TIMESTAMP(6) WHERE id=1
                """)).isOne();
        rejects(3819, "ck_administration_guard_admin_receipt", """
                UPDATE administration_guard SET first_admin_provisioned_at=NULL WHERE id=1
                """);
        assertThat(jdbc.queryForMap("""
                SELECT HEX(policy_seed_operation_id) AS seed_id, HEX(first_admin_operation_id) AS grant_id,
                       first_admin_user_id FROM administration_guard WHERE id=1
                """)).containsEntry("seed_id", "0102030405060708090A0B0C0D0E0F10")
                .containsEntry("grant_id", "02".repeat(16))
                .containsEntry("first_admin_user_id", BigInteger.valueOf(42));
    }

    @Test
    void enforcesSameUserAndUniqueProvenanceAndSupportsExplicitDetachment() throws Exception {
        flyway("3").migrate();
        seedExistingData();
        assertThat(jdbc.update("UPDATE auth_sessions SET source_auth_flow_id=51 WHERE id=21")).isOne();
        rejects(1062, "uq_auth_sessions_user_source_flow",
                "UPDATE auth_sessions SET source_auth_flow_id=51 WHERE id=22");
        rejects(1452, "fk_auth_sessions_source_flow", "UPDATE auth_sessions SET source_auth_flow_id=52 WHERE id=22");
        rejects(1452, "fk_auth_sessions_source_flow", "UPDATE auth_sessions SET source_auth_flow_id=99 WHERE id=22");
        var session = jdbc.queryForMap("SELECT * FROM auth_sessions WHERE id=22");
        var replayHistory = jdbc.queryForList(REPLAY_HISTORY + "WHERE session_id=22 ORDER BY id");
        assertThat(jdbc.update("UPDATE auth_sessions SET source_auth_flow_id=53 WHERE id=22")).isOne();
        rejects(1451, "fk_auth_sessions_source_flow", "DELETE FROM auth_flows WHERE id=53");
        assertThat(jdbc.update("UPDATE auth_sessions SET source_auth_flow_id=NULL WHERE id=22")).isOne();
        assertThat(jdbc.update("DELETE FROM auth_flows WHERE id=53")).isOne();
        assertThat(jdbc.queryForMap("SELECT * FROM auth_sessions WHERE id=22")).isEqualTo(session);
        assertThat(jdbc.queryForList(REPLAY_HISTORY + "WHERE session_id=22 ORDER BY id")).isEqualTo(replayHistory);
        assertThat(new AuthenticationStateRepository(jdbc).accessRole(BigInteger.ONE, BigInteger.valueOf(22)))
                .contains(Role.STUDENT);
    }

    @Test
    void concurrentGuardLockExcludesSecondTransactionAndRollbackReleasesIt() throws SQLException {
        flyway("3").migrate();
        try (var owner = database.getConnection(); var contender = database.getConnection()) {
            owner.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            contender.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            owner.setAutoCommit(false);
            contender.setAutoCommit(false);

            assertThat(lockGuard(owner)).isNull();
            try (var write = owner.createStatement()) {
                assertThat(write.executeUpdate(SEED_RECEIPT)).isOne();
            }
            try (var attempt = contender.createStatement()) {
                // NOWAIT proves contention without a scheduling delay or a timeout-based assertion.
                assertThatThrownBy(() -> attempt.executeQuery(GUARD_LOCK + " FOR UPDATE NOWAIT"))
                        .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getErrorCode()).isEqualTo(3572));
            }
            owner.rollback();
            contender.rollback();

            assertThat(lockGuard(contender)).isNull();
            try (var write = contender.createStatement()) {
                assertThat(write.executeUpdate(SEED_RECEIPT)).isOne();
            }
            contender.commit();
            assertThat(jdbc.queryForObject(GUARD_LOCK, String.class)).isEqualTo("0102030405060708090A0B0C0D0E0F10");
        }
    }

    private Flyway flyway(String version) {
        return Flyway.configure().dataSource(database).locations("classpath:db/migration")
                .target(version).cleanDisabled(true).load();
    }

    private static String lockGuard(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var row = statement.executeQuery(GUARD_LOCK + " FOR UPDATE")) {
            assertThat(row.next()).isTrue();
            return row.getString(1);
        }
    }

    private void seedExistingData() throws Exception {
        jdbc.update("""
                INSERT INTO users (id,first_name,student_id,student_id_normalized,university_email,email_normalized,
                                   email_verified_at,role,status,created_at,updated_at)
                VALUES (1,'Fixture','fixture-1','fixture-1','student@example.edu','student@example.edu',
                        UTC_TIMESTAMP(6),'STUDENT','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                       (2,'Pending',NULL,NULL,NULL,NULL,NULL,'STUDENT','PENDING_PROFILE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        jdbc.update("""
                INSERT INTO provider_identities (user_id,provider,provider_subject,linked_at)
                VALUES (1,'GOOGLE','fixture-subject',UTC_TIMESTAMP(6))
                """);
        var device = new ECKeyGenerator(Curve.P_256).generate().toPublicJWK();
        jdbc.update("""
                INSERT INTO trusted_devices (id,user_id,device_key_hash,verified_at,public_key_jwk)
                VALUES (11,1,?,UTC_TIMESTAMP(6),?)
                """, HexFormat.of().formatHex(device.computeThumbprint().decode()), device.toJSONString());
        jdbc.update("""
                INSERT INTO auth_sessions (id,user_id,device_id,refresh_token_hash,created_at,expires_at)
                VALUES (21,1,11,SHA2('fixture-session-21',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 30 DAY),
                       (22,1,11,SHA2('fixture-session-22',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 30 DAY)
                """);
        jdbc.update("""
                INSERT INTO institutional_policies (id,student_id_pattern,allowed_email_domains,revision,updated_by_user_id,updated_at)
                VALUES (1,'fixture-1',JSON_ARRAY('example.edu'),4,1,UTC_TIMESTAMP(6))
                """);
        for (int challenge : List.of(41, 42, 43)) {
            jdbc.update("""
                    INSERT INTO provider_auth_challenges
                        (id,challenge_id_digest,provider,purpose,nonce_digest,created_at,expires_at,consumed_at)
                    VALUES (?,UNHEX(SHA2(?,256)),'GOOGLE','SIGN_IN',UNHEX(SHA2(?,256)),
                            UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE,UTC_TIMESTAMP(6)+INTERVAL 4 MINUTE,UTC_TIMESTAMP(6))
                    """, challenge, "challenge-" + challenge, "nonce-" + challenge);
        }
        jdbc.update("""
                INSERT INTO auth_flows (id,user_id,source_challenge_id,flow_jti_digest,created_at,expires_at,completed_at)
                VALUES (51,1,41,UNHEX(SHA2('fixture-flow-51',256)),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 15 MINUTE,UTC_TIMESTAMP(6)),
                       (52,2,42,UNHEX(SHA2('fixture-flow-52',256)),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 15 MINUTE,NULL),
                       (53,1,43,UNHEX(SHA2('fixture-flow-53',256)),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 15 MINUTE,UTC_TIMESTAMP(6))
                """);
        jdbc.update("""
                INSERT INTO email_challenges (id,user_id,purpose,destination_email,code_digest,created_at,expires_at,
                                              resend_available_at,consumed_at,auth_flow_id)
                VALUES (31,1,'REGISTRATION','student@example.edu',UNHEX(SHA2('fixture-otp',256)),
                        UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE,UTC_TIMESTAMP(6)+INTERVAL 9 MINUTE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),51)
                """);
        jdbc.update("""
                INSERT INTO consumed_refresh_tokens (session_id,token_digest,consumed_at)
                VALUES (22,UNHEX(SHA2('fixture-spent-22',256)),UTC_TIMESTAMP(6))
                """);
    }

    private void rejects(int errorCode, String constraint, String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).rootCause().isInstanceOfSatisfying(SQLException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(errorCode);
            assertThat(error.getMessage()).contains(constraint);
        });
    }
}
