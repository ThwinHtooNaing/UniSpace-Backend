package com.unispace.infrastructure.outbox;

import com.unispace.audit.application.AuditWriter;
import com.unispace.audit.application.AuditWriter.Action;
import com.unispace.notification.application.InboxDelivery;
import com.unispace.notification.application.NotificationOutbox;
import com.unispace.notification.application.NotificationOutbox.ConflictingChangeException;
import com.unispace.notification.application.NotificationOutbox.Kind;
import com.unispace.notification.application.NotificationOutbox.Notice;
import com.unispace.notification.persistence.JdbcInboxDelivery;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "unispace.mysql.tests", matches = "true")
@Testcontainers
@Import(AdministrationPersistenceTests.FailureFixture.class)
class AdministrationPersistenceTests {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    private static final BigInteger ACTOR = BigInteger.ONE;
    private static final Notice NOTICE = new Notice(Kind.ROLE_REQUEST_DECIDED, BigInteger.TWO, BigInteger.TEN);

    @Autowired JdbcTemplate db;
    @Autowired AuditWriter audit;
    @Autowired NotificationOutbox producer;
    @Autowired JdbcOutbox outbox;
    @Autowired OutboxWorker worker;
    @Autowired InboxDelivery inbox;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AtomicBoolean failAfterWrite;
    TransactionTemplate transaction;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.flyway.url", MYSQL::getJdbcUrl);
        properties.add("spring.flyway.user", MYSQL::getUsername);
        properties.add("spring.flyway.password", MYSQL::getPassword);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureFixture {
        @Bean AtomicBoolean failAfterWrite() {
            return new AtomicBoolean();
        }

        @Bean @Primary InboxDelivery testDelivery(JdbcInboxDelivery delivery, AtomicBoolean failAfterWrite) {
            return (eventId, notice) -> {
                delivery.deliver(eventId, notice);
                if (failAfterWrite.get()) throw new IllegalStateException("Simulated failure after inbox write");
            };
        }
    }

    @BeforeEach
    void prepare() {
        transaction = new TransactionTemplate(transactions);
        failAfterWrite.set(false);
        db.update("DELETE FROM notifications");
        db.update("DELETE FROM outbox_events");
        db.update("DELETE FROM audit_events");
        db.update("DELETE FROM users");
        db.update("""
                INSERT INTO users (id,first_name,role,status,email_verified_at,created_at,updated_at)
                VALUES (1,'Original','ADMIN','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                       (2,'Recipient','STUDENT','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
    }

    @Test
    void requiredWritesCannotEscapeTheBusinessTransaction() {
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> audit.record(ACTOR, Action.ROLE_REQUEST_DECIDED, NOTICE.targetId(), id))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> producer.enqueue(id, NOTICE)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> transaction.execute(status -> worker.processNext()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void businessAuditAndOutboxCommitTogetherAndDeliveryUsesMinimalData() {
        var correlation = UUID.randomUUID();
        try (var executor = Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(status -> {
                changeBusinessState();
                audit.record(ACTOR, Action.ROLE_REQUEST_DECIDED, NOTICE.targetId(), correlation);
                producer.enqueue(correlation, NOTICE);
                assertThat(get(executor.submit(worker::processNext))).isFalse();
            });
        }
        assertThat(firstName()).isEqualTo("Changed");
        assertThat(db.queryForMap("""
                SELECT actor_user_id,action,target_type,target_id,details,correlation_id,
                       TIMESTAMPDIFF(DAY,occurred_at,retention_until) AS retention_days
                FROM audit_events
                """)).containsEntry("actor_user_id", ACTOR).containsEntry("action", "ROLE_REQUEST_DECIDED")
                .containsEntry("target_type", "ROLE_REQUEST").containsEntry("target_id", "10")
                .containsEntry("details", "{}").containsEntry("correlation_id", correlation.toString())
                .containsEntry("retention_days", 365L);
        assertThat(worker.processNext()).isTrue();
        assertThat(outboxStatus()).isEqualTo("DONE");
        assertThat(db.queryForMap("""
                SELECT user_id,category,notification_type,
                       JSON_UNQUOTE(JSON_EXTRACT(payload,'$.targetType')) AS target_type,
                       JSON_UNQUOTE(JSON_EXTRACT(payload,'$.targetId')) AS target_id,
                       JSON_LENGTH(payload) AS fields, TIMESTAMPDIFF(DAY,created_at,expires_at) AS expiry_days
                FROM notifications
                """)).containsEntry("user_id", BigInteger.TWO).containsEntry("category", "ROLE_UPDATES")
                .containsEntry("notification_type", "ROLE_REQUEST_DECIDED").containsEntry("target_type", "ROLE_REQUEST")
                .containsEntry("target_id", "10").containsEntry("fields", 2L).containsEntry("expiry_days", 90L);
    }

    @Test
    void failedBusinessMutationRollsBackItsAuditAndPendingNotification() {
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            changeBusinessState();
            audit.record(ACTOR, Action.ROLE_REQUEST_DECIDED, NOTICE.targetId(), id);
            producer.enqueue(id, NOTICE);
            throw new IllegalStateException("Simulated business failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(firstName()).isEqualTo("Original");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isZero();
        assertThat(worker.processNext()).isFalse();
    }

    @Test
    void auditOrOutboxFailureRollsBackTheProtectedMutation() {
        var id = UUID.randomUUID();
        enqueue(id, NOTICE);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            changeBusinessState();
            audit.record(BigInteger.TEN, Action.ROLE_REQUEST_DECIDED, NOTICE.targetId(), id);
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(firstName()).isEqualTo("Original");

        var conflict = new Notice(NOTICE.kind(), NOTICE.recipientId(), BigInteger.ONE);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            changeBusinessState();
            audit.record(ACTOR, Action.ROLE_REQUEST_DECIDED, NOTICE.targetId(), id);
            producer.enqueue(id, conflict);
        })).isInstanceOf(ConflictingChangeException.class);
        assertThat(firstName()).isEqualTo("Original");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT aggregate_id FROM outbox_events", String.class)).isEqualTo("10");
    }

    @Test
    void concurrentProducerRetriesKeepOneEventPerRecipient() throws Exception {
        var key = UUID.randomUUID();
        race(() -> {
            enqueue(key, NOTICE);
            return true;
        });
        enqueue(key, new Notice(NOTICE.kind(), ACTOR, NOTICE.targetId()));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isEqualTo(2);
        assertThat(worker.processNext()).isTrue();
        assertThat(worker.processNext()).isTrue();
        assertThat(worker.processNext()).isFalse();
        assertThat(db.queryForObject("SELECT COUNT(DISTINCT user_id) FROM notifications", Integer.class)).isEqualTo(2);
    }

    @Test
    void competingWorkersDeliverOneEventOnce() throws Exception {
        enqueue(UUID.randomUUID(), NOTICE);
        assertThat(race(worker::processNext)).containsExactlyInAnyOrder(true, false);
        assertThat(db.queryForObject("SELECT attempt_count FROM outbox_events", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class)).isEqualTo(1);
    }

    @Test
    void expiredClaimIsRecoveredAndThePreviousLeaseCannotAcknowledgeIt() {
        enqueue(UUID.randomUUID(), NOTICE);
        var abandoned = outbox.claimNext().orElseThrow();
        expireClaim();
        var recovered = outbox.claimNext().orElseThrow();
        assertThat(recovered.event().id()).isEqualTo(abandoned.event().id());
        assertThat(recovered.leaseToken()).isNotEqualTo(abandoned.leaseToken());
        Boolean staleAcknowledged = transaction.execute(status -> outbox.complete(abandoned));
        assertThat(staleAcknowledged).isFalse();
        outbox.fail(abandoned, true);
        assertThat(outboxStatus()).isEqualTo("PROCESSING");
        expireClaim();
        assertThat(worker.processNext()).isTrue();
        assertThat(outboxStatus()).isEqualTo("DONE");
        assertThat(db.queryForObject("SELECT attempt_count FROM outbox_events", Integer.class)).isEqualTo(3);
    }

    @Test
    void replayDoesNotResetAnExistingInboxRecord() {
        enqueue(UUID.randomUUID(), NOTICE);
        var abandoned = outbox.claimNext().orElseThrow();
        transaction.executeWithoutResult(status -> inbox.deliver(abandoned.event().id(), NOTICE));
        db.update("UPDATE notifications SET read_at=UTC_TIMESTAMP(6),deleted_at=UTC_TIMESTAMP(6)");
        var original = db.queryForMap("SELECT * FROM notifications");
        expireClaim();
        assertThat(worker.processNext()).isTrue();
        assertThat(db.queryForMap("SELECT * FROM notifications")).isEqualTo(original);
        assertThat(outboxStatus()).isEqualTo("DONE");
    }

    @Test
    void deliveryFailureRollsBackTheInboxAndRetriesAfterBackoff() {
        enqueue(UUID.randomUUID(), NOTICE);
        failAfterWrite.set(true);
        assertThat(worker.processNext()).isTrue();
        assertThat(outboxStatus()).isEqualTo("PENDING");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT next_attempt_at>UTC_TIMESTAMP(6) FROM outbox_events", Boolean.class))
                .isTrue();
        assertThat(worker.processNext()).isFalse();
        makeRetryDue();
        failAfterWrite.set(false);
        assertThat(worker.processNext()).isTrue();
        assertThat(outboxStatus()).isEqualTo("DONE");
        assertThat(db.queryForObject("SELECT attempt_count FROM outbox_events", Integer.class)).isEqualTo(2);
    }

    @Test
    void exhaustedRetriesAndMalformedKnownEventsRemainVisibleAsFailed() {
        failAfterWrite.set(true);
        enqueue(UUID.randomUUID(), NOTICE);
        for (int attempt = 1; attempt <= JdbcOutbox.MAX_ATTEMPTS; attempt++) {
            assertThat(worker.processNext()).isTrue();
            makeRetryDue();
        }
        assertThat(outboxStatus()).isEqualTo("FAILED");
        assertThat(worker.processNext()).isFalse();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class)).isZero();

        enqueue(UUID.randomUUID(), NOTICE);
        db.update("UPDATE outbox_events SET payload=JSON_OBJECT() WHERE status='PENDING'");
        assertThat(worker.processNext()).isTrue();
        assertThat(db.queryForList("SELECT status FROM outbox_events ORDER BY id", String.class))
                .containsExactly("FAILED", "FAILED");
        assertThat(worker.processNext()).isFalse();
    }

    @Test
    void crashOnFinalClaimExhaustsTheEventAndUnknownVersionsStayPending() {
        enqueue(UUID.randomUUID(), NOTICE);
        outbox.claimNext().orElseThrow();
        db.update("UPDATE outbox_events SET attempt_count=?", JdbcOutbox.MAX_ATTEMPTS);
        expireClaim();
        assertThat(worker.processNext()).isFalse();
        assertThat(outboxStatus()).isEqualTo("FAILED");
        enqueue(UUID.randomUUID(), NOTICE);
        db.update("UPDATE outbox_events SET event_version=2 WHERE status='PENDING'");
        assertThat(worker.processNext()).isFalse();
        assertThat(db.queryForObject("SELECT status FROM outbox_events WHERE event_version=2", String.class))
                .isEqualTo("PENDING");
    }

    private String outboxStatus() {
        return db.queryForObject("SELECT status FROM outbox_events", String.class);
    }

    private void enqueue(UUID id, Notice notice) {
        transaction.executeWithoutResult(status -> producer.enqueue(id, notice));
    }

    private void expireClaim() {
        db.update("UPDATE outbox_events SET claim_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND");
    }

    private void makeRetryDue() {
        db.update("UPDATE outbox_events SET next_attempt_at=UTC_TIMESTAMP(6) WHERE status='PENDING'");
    }

    private void changeBusinessState() {
        db.update("UPDATE users SET first_name='Changed' WHERE id=?", new BigDecimal(ACTOR));
    }

    private String firstName() {
        return db.queryForObject("SELECT first_name FROM users WHERE id=?", String.class, new BigDecimal(ACTOR));
    }

    private static List<Boolean> race(Callable<Boolean> operation) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> competitor = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out starting worker");
            return operation.call();
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(competitor);
            var second = executor.submit(competitor);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            throw new AssertionError(failure);
        }
    }
}
