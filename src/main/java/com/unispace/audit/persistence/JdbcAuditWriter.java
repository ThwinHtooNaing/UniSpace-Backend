package com.unispace.audit.persistence;

import com.unispace.audit.application.AuditWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAuditWriter implements AuditWriter {
    private final JdbcTemplate jdbc;

    public JdbcAuditWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(BigInteger actorUserId, Action action, BigInteger targetId, UUID correlationId) {
        Objects.requireNonNull(actorUserId);
        Objects.requireNonNull(targetId);
        if (targetId.signum() <= 0 || targetId.bitLength() > Long.SIZE
                || actorUserId.signum() <= 0 || actorUserId.bitLength() > Long.SIZE) {
            throw new IllegalArgumentException("Expected an unsigned resource identifier");
        }
        jdbc.update("""
                INSERT INTO audit_events (actor_user_id,action,target_type,target_id,details,correlation_id,
                                          occurred_at,retention_until)
                VALUES (?,?,?,?,JSON_OBJECT(),?,UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 365 DAY))
                """, new BigDecimal(actorUserId), action.name(), action.targetType(),
                targetId.toString(), correlationId.toString());
    }
}
