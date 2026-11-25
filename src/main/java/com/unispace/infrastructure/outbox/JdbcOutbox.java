package com.unispace.infrastructure.outbox;

import com.unispace.notification.application.NotificationOutbox;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcOutbox implements NotificationOutbox {
    private static final Logger log = LoggerFactory.getLogger(JdbcOutbox.class);
    static final int MAX_ATTEMPTS = 5;
    private static final int LEASE_SECONDS = 60;
    private static final int INITIAL_RETRY_SECONDS = 5;
    private static final String INBOX_EVENTS = """
            SELECT id,aggregate_type,aggregate_id,attempt_count,
                   JSON_UNQUOTE(JSON_EXTRACT(payload,'$.recipientId')) AS recipient_id,
                   JSON_UNQUOTE(JSON_EXTRACT(payload,'$.kind')) AS kind
            FROM outbox_events WHERE event_type='INBOX_NOTIFICATION' AND event_version=1 AND
            """;
    private static final String CURRENT_LEASE = """
            WHERE id=? AND status='PROCESSING' AND claimed_by=? AND claim_expires_at>UTC_TIMESTAMP(6)
            """;

    record Event(BigInteger id, String targetType, String targetId, int attempts, String recipientId, String kind) {}
    record Claim(Event event, String leaseToken) {}

    private final JdbcTemplate jdbc;

    public JdbcOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID changeId, Notice notice) {
        String key = "inbox:" + changeId.toString() + ":" + notice.recipientId();
        String type = notice.kind().targetType();
        String target = notice.targetId().toString();
        String recipient = notice.recipientId().toString();
        String kind = notice.kind().name();
        jdbc.update("""
                INSERT INTO outbox_events (event_type,aggregate_type,aggregate_id,event_version,payload,status,
                                           attempt_count,next_attempt_at,deduplication_key,created_at)
                VALUES ('INBOX_NOTIFICATION',?,?,1,JSON_OBJECT('recipientId',?,'kind',?),'PENDING',
                        0,UTC_TIMESTAMP(6),?,UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE id=outbox_events.id
                """, type, target, recipient, kind, key);
        // A duplicate is safe only when it represents the same committed business event.
        if (jdbc.queryForList("""
                SELECT id FROM outbox_events WHERE deduplication_key=? AND event_type='INBOX_NOTIFICATION'
                  AND event_version=1 AND aggregate_type=? AND aggregate_id=?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.recipientId'))=?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.kind'))=?
                FOR SHARE
                """, BigDecimal.class, key, type, target, recipient, kind).isEmpty()) {
            throw new ConflictingChangeException();
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<Claim> claimNext() {
        var candidate = find("""
                status='PROCESSING' AND claim_expires_at<=UTC_TIMESTAMP(6)
                ORDER BY claim_expires_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """);
        if (candidate.isPresent() && candidate.get().attempts() >= MAX_ATTEMPTS) {
            BigInteger id = candidate.get().id();
            jdbc.update("""
                    UPDATE outbox_events SET status='FAILED',claimed_by=NULL,claim_expires_at=NULL
                    WHERE id=?
                    """, new BigDecimal(id));
            log.warn("Outbox event {} exhausted its attempts after its lease expired", id);
            candidate = Optional.empty();
        }
        return candidate.or(() -> find("""
                status='PENDING' AND next_attempt_at<=UTC_TIMESTAMP(6)
                ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """)).map(this::claim);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean complete(Claim claim) {
        return jdbc.update("""
                UPDATE outbox_events SET status='DONE',processed_at=UTC_TIMESTAMP(6),
                                         claimed_by=NULL,claim_expires_at=NULL
                """ + CURRENT_LEASE, new BigDecimal(claim.event().id()), claim.leaseToken()) == 1;
    }

    @Transactional
    public void fail(Claim claim, boolean permanent) {
        int attempt = claim.event().attempts() + 1;
        boolean terminal = permanent || attempt >= MAX_ATTEMPTS;
        int delay = terminal ? 0 : INITIAL_RETRY_SECONDS << (attempt - 1);
        int updated = jdbc.update("""
                UPDATE outbox_events SET status=?,next_attempt_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),
                                         claimed_by=NULL,claim_expires_at=NULL
                """ + CURRENT_LEASE, terminal ? "FAILED" : "PENDING", delay,
                new BigDecimal(claim.event().id()), claim.leaseToken());
        if (updated == 1) {
            log.warn("Outbox event {} attempt {} {}", claim.event().id(), attempt,
                    terminal ? "failed permanently" : "failed and will retry");
        }
    }

    private Optional<Event> find(String eligibility) {
        return jdbc.query(INBOX_EVENTS + eligibility, rows -> rows.next() ? Optional.of(new Event(
                rows.getBigDecimal("id").toBigIntegerExact(), rows.getString("aggregate_type"),
                rows.getString("aggregate_id"), rows.getInt("attempt_count"),
                rows.getString("recipient_id"), rows.getString("kind"))) : Optional.empty());
    }

    private Claim claim(Event event) {
        // A new token on each claim also fences a previous attempt by the same worker.
        String token = UUID.randomUUID().toString();
        jdbc.update("""
                UPDATE outbox_events SET status='PROCESSING',attempt_count=attempt_count+1,claimed_by=?,
                                         claim_expires_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6))
                WHERE id=?
                """, token, LEASE_SECONDS, new BigDecimal(event.id()));
        return new Claim(event, token);
    }
}
