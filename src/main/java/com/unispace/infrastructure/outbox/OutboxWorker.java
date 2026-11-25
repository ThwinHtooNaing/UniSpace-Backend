package com.unispace.infrastructure.outbox;

import com.unispace.notification.application.InboxDelivery;
import com.unispace.notification.application.NotificationOutbox.Kind;
import com.unispace.notification.application.NotificationOutbox.Notice;
import java.math.BigInteger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxWorker {
    private final JdbcOutbox outbox;
    private final InboxDelivery inbox;
    private final TransactionTemplate transaction;

    public OutboxWorker(JdbcOutbox outbox, InboxDelivery inbox, PlatformTransactionManager transactions) {
        this.outbox = outbox;
        this.inbox = inbox;
        this.transaction = new TransactionTemplate(transactions);
    }

    /** Claim in one transaction, then commit the inbox write and acknowledgment together. */
    @Transactional(propagation = Propagation.NEVER)
    public boolean processNext() {
        var claimed = outbox.claimNext();
        if (claimed.isEmpty()) return false;
        var claim = claimed.get();
        Notice notice;
        try {
            notice = decode(claim.event());
        } catch (IllegalArgumentException malformed) {
            outbox.fail(claim, true);
            return true;
        }
        try {
            transaction.executeWithoutResult(status -> {
                inbox.deliver(claim.event().id(), notice);
                if (!outbox.complete(claim)) status.setRollbackOnly();
            });
        } catch (RuntimeException deliveryFailed) {
            outbox.fail(claim, false);
        }
        return true;
    }

    private static Notice decode(JdbcOutbox.Event event) {
        if (event.kind() == null || event.recipientId() == null || event.recipientId().length() > 20) {
            throw new IllegalArgumentException("Invalid inbox payload");
        }
        var kind = Kind.valueOf(event.kind());
        if (!kind.targetType().equals(event.targetType()) || event.targetId().length() > 20) {
            throw new IllegalArgumentException("Invalid inbox target");
        }
        return new Notice(kind, new BigInteger(event.recipientId()), new BigInteger(event.targetId()));
    }
}
