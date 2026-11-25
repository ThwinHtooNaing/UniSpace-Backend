package com.unispace.notification.application;

import com.unispace.notification.application.NotificationOutbox.Notice;
import java.math.BigInteger;

public interface InboxDelivery {
    void deliver(BigInteger outboxEventId, Notice notice);
}
