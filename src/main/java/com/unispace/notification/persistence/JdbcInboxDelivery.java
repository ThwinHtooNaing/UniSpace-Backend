package com.unispace.notification.persistence;

import com.unispace.notification.application.InboxDelivery;
import com.unispace.notification.application.NotificationOutbox.Notice;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcInboxDelivery implements InboxDelivery {
    private final JdbcTemplate jdbc;

    public JdbcInboxDelivery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deliver(BigInteger outboxEventId, Notice notice) {
        jdbc.update("""
                INSERT INTO notifications (user_id,event_key,category,notification_type,payload,created_at,expires_at)
                VALUES (?,?,?,?,JSON_OBJECT('targetType',?,'targetId',?),UTC_TIMESTAMP(6),
                        DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 90 DAY))
                ON DUPLICATE KEY UPDATE event_key=notifications.event_key
                """, new BigDecimal(notice.recipientId()), "outbox:" + outboxEventId, notice.kind().category(),
                notice.kind().name(), notice.kind().targetType(), notice.targetId().toString());
    }
}
