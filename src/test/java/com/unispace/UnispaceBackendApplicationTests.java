package com.unispace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.flywaydb.core.Flyway;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "unispace.mysql.tests", matches = "true")
@Testcontainers
@Transactional
class UnispaceBackendApplicationTests {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("UNISPACE_TEST_DB_HOST", MYSQL::getHost);
        properties.add("UNISPACE_TEST_DB_PORT", MYSQL::getFirstMappedPort);
        properties.add("UNISPACE_TEST_DB_NAME", MYSQL::getDatabaseName);
        properties.add("spring.datasource.url", () -> "${unispace.database.jdbc-url}");
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.flyway.url", () -> "${spring.datasource.url}");
        properties.add("spring.flyway.user", () -> "${spring.datasource.username}");
        properties.add("spring.flyway.password", () -> "${spring.datasource.password}");
    }

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @Test
    void contextLoads() {
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(entityManager.getEntityManagerFactory().getProperties())
                .containsEntry("hibernate.hbm2ddl.auto", "validate");
        assertThat(jdbc.queryForMap("SELECT @@session.time_zone AS zone, @@session.transaction_isolation AS isolation_level"))
                .containsEntry("zone", "+00:00")
                .containsEntry("isolation_level", "REPEATABLE-READ");

        assertThat(jdbc.queryForList("""
                SELECT table_name, engine FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                """)).hasSize(18).allSatisfy(table -> assertThat(table.get("engine")).isEqualTo("InnoDB"));
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(table_name, ':', GROUP_CONCAT(column_name ORDER BY ordinal_position))
                FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE() AND constraint_name = 'PRIMARY'
                  AND table_name <> 'flyway_schema_history'
                GROUP BY table_name
                """, String.class)).containsExactlyInAnyOrder(
                "users:id", "provider_identities:id", "trusted_devices:id", "auth_sessions:id",
                "email_challenges:id", "institutional_policies:id", "role_requests:id",
                "channels:id", "channel_memberships:user_id", "membership_requests:id",
                "files:id", "upload_sessions:id", "upload_parts:id", "video_progress:user_id,file_id",
                "notifications:id", "notification_preferences:user_id,category", "audit_events:id", "outbox_events:id");
    }

    @Test
    void validatesForeignKeysAndDeleteRules() {
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(k.table_name, ':', GROUP_CONCAT(k.column_name ORDER BY k.ordinal_position),
                    '->', k.referenced_table_name, '(',
                    GROUP_CONCAT(k.referenced_column_name ORDER BY k.ordinal_position), '):', r.delete_rule)
                FROM information_schema.key_column_usage k
                JOIN information_schema.referential_constraints r
                  ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name
                WHERE k.table_schema = DATABASE() AND k.referenced_table_name IS NOT NULL
                GROUP BY k.table_name, k.constraint_name, k.referenced_table_name, r.delete_rule
                """, String.class)).containsExactlyInAnyOrder(
                "provider_identities:user_id->users(id):RESTRICT",
                "trusted_devices:user_id->users(id):RESTRICT",
                "auth_sessions:user_id->users(id):RESTRICT",
                "auth_sessions:user_id,device_id->trusted_devices(user_id,id):RESTRICT",
                "email_challenges:user_id->users(id):RESTRICT",
                "institutional_policies:updated_by_user_id->users(id):RESTRICT",
                "role_requests:requester_user_id->users(id):RESTRICT",
                "role_requests:decided_by_user_id->users(id):RESTRICT",
                "channels:creator_user_id->users(id):RESTRICT",
                "channels:owner_user_id->users(id):RESTRICT",
                "channels:id,owner_user_id->channel_memberships(channel_id,user_id):RESTRICT",
                "channel_memberships:user_id->users(id):RESTRICT",
                "channel_memberships:channel_id->channels(id):RESTRICT",
                "membership_requests:requester_user_id->users(id):RESTRICT",
                "membership_requests:target_channel_id->channels(id):RESTRICT",
                "membership_requests:source_channel_id->channels(id):RESTRICT",
                "membership_requests:decided_by_user_id->users(id):RESTRICT",
                "files:channel_id->channels(id):RESTRICT",
                "files:restore_channel_id->channels(id):RESTRICT",
                "files:uploader_user_id->users(id):RESTRICT",
                "upload_sessions:file_id->files(id):RESTRICT",
                "upload_sessions:user_id->users(id):RESTRICT",
                "upload_sessions:user_id,device_id->trusted_devices(user_id,id):RESTRICT",
                "upload_parts:upload_session_id->upload_sessions(id):CASCADE",
                "video_progress:user_id->users(id):RESTRICT",
                "video_progress:file_id->files(id):RESTRICT",
                "notifications:user_id->users(id):RESTRICT",
                "notification_preferences:user_id->users(id):RESTRICT",
                "audit_events:actor_user_id->users(id):RESTRICT");
    }

    @Test
    void validatesUniqueIndexes() {
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(table_name, ':', GROUP_CONCAT(column_name ORDER BY seq_in_index))
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND non_unique = 0 AND index_name <> 'PRIMARY'
                GROUP BY table_name, index_name
                """, String.class)).containsExactlyInAnyOrder(
                "users:student_id_normalized", "users:email_normalized",
                "provider_identities:provider,provider_subject", "provider_identities:user_id,provider",
                "trusted_devices:user_id,device_key_hash", "trusted_devices:user_id,id",
                "auth_sessions:refresh_token_hash", "channels:name_normalized",
                "channel_memberships:channel_id,user_id", "membership_requests:requester_user_id,pending_target_channel_id",
                "files:storage_key", "files:channel_id,active_filename", "upload_sessions:user_id,idempotency_key",
                "upload_parts:upload_session_id,part_number", "notifications:user_id,event_key",
                "outbox_events:deduplication_key");
    }

    @Test
    void preservesOneMembershipAndRequiresAnOwnerFromTheSameChannel() {
        assertSqlError(1452, "UPDATE channels SET owner_user_id = 1 WHERE id = 11");
        jdbc.update("INSERT INTO channel_memberships VALUES (1, 11, UTC_TIMESTAMP(6))");
        assertSqlError(1062, "INSERT INTO channel_memberships VALUES (1, 12, UTC_TIMESTAMP(6))");
        assertSqlError(1452, "UPDATE channels SET owner_user_id = 1 WHERE id = 12");
        jdbc.update("UPDATE channels SET owner_user_id = 1 WHERE id = 11");
        assertSqlError(1451, "DELETE FROM channel_memberships WHERE user_id = 1");
        jdbc.update("UPDATE channels SET owner_user_id = NULL WHERE id = 11");
        assertThat(jdbc.update("DELETE FROM channel_memberships WHERE user_id = 1")).isEqualTo(1);
    }

    @Test
    void preservesRequestHistoryAndDeletedFileWhenChannelIsRemoved() {
        String request = """
                INSERT INTO membership_requests
                    (requester_user_id, target_channel_id, target_channel_id_snapshot, request_type, status, requested_at)
                VALUES (1, 11, 11, 'JOIN', 'PENDING', UTC_TIMESTAMP(6))
                """;
        jdbc.update(request);
        assertSqlError(1062, request);
        jdbc.update("UPDATE membership_requests SET status = 'REJECTED'");
        jdbc.update(request);
        assertSqlError(1451, "DELETE FROM channels WHERE id = 11");
        assertSqlError(3819, "UPDATE membership_requests SET target_channel_id = NULL WHERE status = 'PENDING'");
        jdbc.update("UPDATE membership_requests SET status = 'CANCELLED' WHERE status = 'PENDING'");
        jdbc.update("UPDATE membership_requests SET target_channel_id = NULL WHERE target_channel_id = 11");

        addFile(21, "Lecture.pdf", "ACTIVE", "object");
        assertSqlError(3819, "UPDATE files SET channel_id = NULL WHERE id = 21");
        jdbc.update("UPDATE files SET status = 'DELETED', deleted_at = UTC_TIMESTAMP(6), channel_id = NULL WHERE id = 21");
        assertThat(jdbc.update("DELETE FROM channels WHERE id = 11")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT target_channel_id_snapshot FROM membership_requests", Long.class))
                .containsExactly(11L, 11L);
        assertThat(jdbc.queryForObject("SELECT status FROM files WHERE id = 21", String.class)).isEqualTo("DELETED");
    }

    @Test
    void distinguishesOpaqueIdentifiersAndEnforcesCaseInsensitiveNames() {
        assertSqlError(1062, "UPDATE channels SET name_normalized = 'FIRST' WHERE id = 12");
        String provider = """
                INSERT INTO provider_identities (user_id, provider, provider_subject, linked_at)
                VALUES (%d, 'GOOGLE', '%s', UTC_TIMESTAMP(6))
                """;
        jdbc.update(provider.formatted(1, "Subject"));
        assertSqlError(1062, provider.formatted(2, "Subject"));
        jdbc.update(provider.formatted(2, "subject"));

        addFile(21, "Lecture.pdf", "ACTIVE", "Path/Object");
        addFile(22, "lecture.pdf", "TRASHED", "path/object");
        assertSqlError(1062, "UPDATE files SET status = 'ACTIVE' WHERE id = 22");
        jdbc.update("UPDATE files SET status = 'TRASHED' WHERE id = 21");
        assertThat(jdbc.update("UPDATE files SET status = 'ACTIVE' WHERE id = 22")).isEqualTo(1);
    }

    @Test
    void requiresTheDeviceToBelongToTheSessionUser() {
        jdbc.update("""
                INSERT INTO trusted_devices (id, user_id, device_key_hash, verified_at)
                VALUES (31, 1, REPEAT('a', 64), UTC_TIMESTAMP(6))
                """);
        String session = """
                INSERT INTO auth_sessions (user_id, device_id, refresh_token_hash, created_at, expires_at)
                VALUES (%d, 31, REPEAT('b', 64), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) + INTERVAL 1 DAY)
                """;
        assertSqlError(1452, session.formatted(2));
        jdbc.update(session.formatted(1));
    }

    @Test
    void validatesUploadIdempotencyPartUniquenessAndCleanup() {
        addFile(21, "Lecture.pdf", "PENDING", null);
        jdbc.update("""
                INSERT INTO trusted_devices (id, user_id, device_key_hash, verified_at)
                VALUES (31, 1, REPEAT('a', 64), UTC_TIMESTAMP(6))
                """);
        String upload = """
                INSERT INTO upload_sessions (file_id, user_id, device_id, idempotency_key, state, created_at, updated_at)
                VALUES (21, %d, 31, 'upload-1', 'INITIATED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """;
        assertSqlError(1452, upload.formatted(2));
        jdbc.update(upload.formatted(1));
        assertSqlError(1062, upload.formatted(1));
        String part = """
                INSERT INTO upload_parts (upload_session_id, part_number, etag, size_bytes, uploaded_at)
                SELECT id, 1, 'etag', 1024, UTC_TIMESTAMP(6) FROM upload_sessions
                """;
        jdbc.update(part);
        assertSqlError(1062, part);
        jdbc.update("DELETE FROM upload_sessions");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM upload_parts", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE id = 21", Long.class)).isEqualTo(1);
    }

    @Test
    void requiresVerifiedEmailForActiveUsersAndCapsInvalidOtpAttempts() {
        assertSqlError(3819, "UPDATE users SET status = 'ACTIVE' WHERE id = 1");
        jdbc.update("UPDATE users SET email_verified_at = UTC_TIMESTAMP(6), status = 'ACTIVE' WHERE id = 1");

        jdbc.update("""
                INSERT INTO email_challenges (user_id, purpose, destination_email, code_digest,
                    created_at, expires_at, resend_available_at)
                VALUES (1, 'REGISTRATION', 'student@example.edu', UNHEX(REPEAT('aa', 32)),
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) + INTERVAL 10 MINUTE, UTC_TIMESTAMP(6) + INTERVAL 1 MINUTE)
                """);
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM email_challenges", Integer.class)).isZero();
        jdbc.update("UPDATE email_challenges SET attempt_count = 5");
        assertSqlError(3819, "UPDATE email_challenges SET attempt_count = 6");
    }

    @Test
    void validatesJpaMappingForUnsignedIdJsonAndMicrosecondTimestamp() {
        LocalDateTime timestamp = LocalDateTime.parse("2026-01-01T12:34:56.123456");
        jdbc.update("""
                INSERT INTO institutional_policies
                    (id, student_id_pattern, allowed_email_domains, revision, updated_by_user_id, updated_at)
                VALUES (41, '[0-9]+', '["example.edu"]', 1, 1, ?)
                """, timestamp);
        var policy = entityManager.find(SchemaValidationPolicy.class, 41L);
        assertThat(policy.allowedEmailDomains).isEqualTo("[\"example.edu\"]");
        assertThat(policy.updatedAt).isEqualTo(timestamp);
    }

    @BeforeEach
    void insertUsersAndChannels() {
        jdbc.update("""
                INSERT INTO users (id, role, status, created_at, updated_at)
                VALUES (1, 'STUDENT', 'PENDING_PROFILE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                       (2, 'STUDENT', 'PENDING_PROFILE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
        jdbc.update("""
                INSERT INTO channels (id, name, name_normalized, creator_user_id, version, created_at, updated_at)
                VALUES (11, 'First', 'first', 1, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
                       (12, 'Second', 'second', 2, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """);
    }

    private void addFile(long id, String filename, String status, String storageKey) {
        jdbc.update("""
                INSERT INTO files (id, channel_id, uploader_user_id, filename, filename_normalized,
                    file_category, mime_type, storage_key, status, created_at, updated_at)
                VALUES (?, 11, 1, ?, ?, 'PDF', 'application/pdf', ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, id, filename, filename, storageKey, status);
    }

    private void assertSqlError(int errorCode, String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).rootCause().isInstanceOfSatisfying(
                SQLException.class, exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

}

@Entity
@Table(name = "institutional_policies")
class SchemaValidationPolicy {
    @Id
    @Column(columnDefinition = "BIGINT UNSIGNED")
    Long id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_email_domains")
    String allowedEmailDomains;

    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
